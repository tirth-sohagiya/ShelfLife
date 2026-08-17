package com.donationmatch.matching.service;

import com.donationmatch.matching.entity.AllocationStatus;
import com.donationmatch.matching.entity.Lot;
import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.event.AllocationEventPublisher;
import com.donationmatch.matching.repository.AllocationRepository;
import com.donationmatch.matching.repository.LotRepository;
import com.donationmatch.matching.repository.RequestRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates matching from both directions - a newly-arrived lot against
 * open requests, and a newly-arrived request against available lots. Each
 * candidate pair is handed off to AllocationService as its own short
 * transaction rather than one transaction spanning the whole loop.
 */
@Service
public class MatchingService {

    private static final List<AllocationStatus> ACTIVE_STATUSES =
            List.of(AllocationStatus.PENDING_PICKUP, AllocationStatus.CONFIRMED);

    private final LotRepository lotRepository;
    private final RequestRepository requestRepository;
    private final AllocationRepository allocationRepository;
    private final AllocationService allocationService;
    private final AllocationEventPublisher allocationEventPublisher;

    public MatchingService(LotRepository lotRepository,
                            RequestRepository requestRepository,
                            AllocationRepository allocationRepository,
                            AllocationService allocationService,
                            AllocationEventPublisher allocationEventPublisher) {
        this.lotRepository = lotRepository;
        this.requestRepository = requestRepository;
        this.allocationRepository = allocationRepository;
        this.allocationService = allocationService;
        this.allocationEventPublisher = allocationEventPublisher;
    }

    /** Called right after a new Lot is saved to the local read model. */
    public void matchNewLot(Lot lot) {
        List<Request> candidates =
                requestRepository.findByItemTypeOrderByCreatedAtAsc(lot.getItemType());

        for (Request request : candidates) {
            allocationService.tryAllocate(lot.getId(), request.getId())
                    .ifPresent(allocationEventPublisher::publishAllocationCreated);

            int lotAllocated = allocationRepository.sumActiveQuantityByLotId(lot.getId(), ACTIVE_STATUSES);
            int lotRemaining = lot.getQuantityAvailable() - lotAllocated;
            if (lotRemaining <= 0) {
                break; // this lot is fully spoken for, stop checking more requests
            }
        }
    }

    /** Called right after a new Request is saved to the local read model. */
    public void matchNewRequest(Request request) {
        List<Lot> candidates = lotRepository
                .findByItemTypeAndExpiryDateAfterOrderByExpiryDateAsc(request.getItemType(), Instant.now());

        for (Lot lot : candidates) {
            allocationService.tryAllocate(lot.getId(), request.getId())
                    .ifPresent(allocationEventPublisher::publishAllocationCreated);

            int requestAllocated = allocationRepository.sumActiveQuantityByRequestId(request.getId(), ACTIVE_STATUSES);
            int requestRemaining = request.getQuantityRequested() - requestAllocated;
            if (requestRemaining <= 0) {
                break; // this request is fully satisfied, stop checking more lots
            }
        }
    }
}
