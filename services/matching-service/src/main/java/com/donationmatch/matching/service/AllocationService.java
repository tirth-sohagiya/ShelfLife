package com.donationmatch.matching.service;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.AllocationStatus;
import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.repository.AllocationRepository;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.repository.RequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Attempts a single allocation between one lot and one request. Kept
 * separate from MatchingService's orchestration loops so that
 * {@code @Transactional} is applied via Spring's proxy rather than a
 * same-class self-invocation, which would bypass it silently.
 */
@Slf4j
@Service
public class AllocationService {

    public static final Duration PICKUP_TTL = Duration.ofHours(24);

    private static final List<AllocationStatus> ACTIVE_STATUSES =
            List.of(AllocationStatus.PENDING_PICKUP, AllocationStatus.CONFIRMED);

    private final LotRepository lotRepository;
    private final RequestRepository requestRepository;
    private final AllocationRepository allocationRepository;
    private final MeterRegistry meterRegistry;

    public AllocationService(LotRepository lotRepository,
                              RequestRepository requestRepository,
                              AllocationRepository allocationRepository,
                              MeterRegistry meterRegistry) {
        this.lotRepository = lotRepository;
        this.requestRepository = requestRepository;
        this.allocationRepository = allocationRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Optional<Allocation> tryAllocate(UUID lotId, UUID requestId) {
        return meterRegistry.timer("matching.allocation.duration")
                .record(() -> doTryAllocate(lotId, requestId));
    }

    private Optional<Allocation> doTryAllocate(UUID lotId, UUID requestId) {

        // Lock both rows in a fixed order (by UUID comparison) to avoid
        // deadlock between concurrent calls involving the same pair.
        Optional<Lot> lotOpt;
        Optional<Request> requestOpt;
        if (lotId.compareTo(requestId) < 0) {
            lotOpt = lotRepository.findByIdForUpdate(lotId);
            requestOpt = requestRepository.findByIdForUpdate(requestId);
        } else {
            requestOpt = requestRepository.findByIdForUpdate(requestId);
            lotOpt = lotRepository.findByIdForUpdate(lotId);
        }

        if (lotOpt.isEmpty() || requestOpt.isEmpty()) {
            return Optional.empty(); // defensive - shouldn't normally happen
        }
        Lot lot = lotOpt.get();
        Request request = requestOpt.get();

        if (lot.getExpiryDate().isBefore(Instant.now().plus(PICKUP_TTL))) {
            log.info("Skipping lot {} - not enough shelf life left for a full pickup window", lotId);
            meterRegistry.counter("matching.allocations.rejected", "reason", "insufficient_shelf_life").increment();
            return Optional.empty();
        }

        int lotAllocated = allocationRepository.sumActiveQuantityByLotId(lotId, ACTIVE_STATUSES);
        int lotRemaining = lot.getQuantityAvailable() - lotAllocated;

        int requestAllocated = allocationRepository.sumActiveQuantityByRequestId(requestId, ACTIVE_STATUSES);
        int requestRemaining = request.getQuantityRequested() - requestAllocated;

        if (lotRemaining <= 0 || requestRemaining <= 0) {
            log.info("No allocation between lot {} and request {} - lot remaining {}, request remaining {}",
                    lotId, requestId, lotRemaining, requestRemaining);
            meterRegistry.counter("matching.allocations.rejected", "reason", "insufficient_capacity").increment();
            return Optional.empty();
        }

        int allocateQty = Math.min(lotRemaining, requestRemaining);

        Allocation allocation = new Allocation();
        allocation.setLotId(lotId);
        allocation.setRequestId(requestId);
        allocation.setQuantity(allocateQty);
        allocation.setStatus(AllocationStatus.PENDING_PICKUP);
        allocation.setPickupDeadline(Instant.now().plus(PICKUP_TTL));
        allocation.setCreatedAt(Instant.now());
        Allocation saved = allocationRepository.save(allocation);
        log.info("Allocated {} unit(s) - lot {} to request {}, allocation {}, pickup deadline {}",
                allocateQty, lotId, requestId, saved.getId(), saved.getPickupDeadline());
        meterRegistry.counter("matching.allocations.created").increment();

        return Optional.of(saved);
    }

    // Conditional update, not a lock - fixed transition, not a
    // stale-prone decision like tryAllocate.
    @Transactional
    public Allocation confirmPickup(UUID allocationId) {
        Allocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));

        if (allocationRepository.confirmIfPending(allocationId) == 0) {
            throw new IllegalStateException(
                    "Allocation " + allocationId + " cannot be confirmed - current status is " + allocation.getStatus());
        }

        allocation.setStatus(AllocationStatus.CONFIRMED);
        log.info("Confirmed pickup for allocation {}", allocationId);
        meterRegistry.counter("matching.allocations.confirmed").increment();
        return allocation;
    }

    public List<Allocation> getAllocationsForLot(UUID lotId) {
        return allocationRepository.findByLotId(lotId);
    }

    @Transactional
    public boolean expireIfOverdue(UUID allocationId) {
        boolean expired = allocationRepository.expireIfPending(allocationId) > 0;
        if (expired) {
            log.info("Expired allocation {} - pickup deadline passed", allocationId);
            meterRegistry.counter("matching.allocations.expired").increment();
        }
        return expired;
    }
}
