package com.donationmatch.matching.service;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.AllocationStatus;
import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.repository.AllocationRepository;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.repository.RequestRepository;
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
@Service
public class AllocationService {

    private static final Duration PICKUP_TTL = Duration.ofHours(48);

    private static final List<AllocationStatus> ACTIVE_STATUSES =
            List.of(AllocationStatus.PENDING_PICKUP, AllocationStatus.CONFIRMED);

    private final LotRepository lotRepository;
    private final RequestRepository requestRepository;
    private final AllocationRepository allocationRepository;

    public AllocationService(LotRepository lotRepository,
                              RequestRepository requestRepository,
                              AllocationRepository allocationRepository) {
        this.lotRepository = lotRepository;
        this.requestRepository = requestRepository;
        this.allocationRepository = allocationRepository;
    }

    /**
     * Attempts to allocate as much as possible between one lot and one
     * request. Returns the quantity actually allocated (0 if nothing was
     * available on either side).
     */
    @Transactional
    public int tryAllocate(UUID lotId, UUID requestId) {

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
            return 0; // defensive - shouldn't normally happen
        }
        Lot lot = lotOpt.get();
        Request request = requestOpt.get();

        int lotAllocated = allocationRepository.sumActiveQuantityByLotId(lotId, ACTIVE_STATUSES);
        int lotRemaining = lot.getQuantityAvailable() - lotAllocated;

        int requestAllocated = allocationRepository.sumActiveQuantityByRequestId(requestId, ACTIVE_STATUSES);
        int requestRemaining = request.getQuantityRequested() - requestAllocated;

        if (lotRemaining <= 0 || requestRemaining <= 0) {
            return 0; // nothing left on one side, nothing to do
        }

        int allocateQty = Math.min(lotRemaining, requestRemaining);

        Allocation allocation = new Allocation();
        allocation.setLotId(lotId);
        allocation.setRequestId(requestId);
        allocation.setQuantity(allocateQty);
        allocation.setStatus(AllocationStatus.PENDING_PICKUP);
        allocation.setPickupDeadline(Instant.now().plus(PICKUP_TTL));
        allocation.setCreatedAt(Instant.now());
        allocationRepository.save(allocation);

        return allocateQty;
    }
}
