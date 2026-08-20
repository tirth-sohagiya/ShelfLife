package com.donationmatch.matching.scheduler;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.AllocationStatus;
import com.donationmatch.matching.repository.AllocationRepository;
import com.donationmatch.matching.service.MatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class PickupDeadlineScheduler {

    private static final long POLL_INTERVAL_MS = 5 * 60 * 1000;

    private final AllocationRepository allocationRepository;
    private final MatchingService matchingService;

    public PickupDeadlineScheduler(AllocationRepository allocationRepository, MatchingService matchingService) {
        this.allocationRepository = allocationRepository;
        this.matchingService = matchingService;
    }

    @Scheduled(fixedRate = POLL_INTERVAL_MS)
    public void releaseOverdueAllocations() {
        List<Allocation> overdue = allocationRepository
                .findByStatusAndPickupDeadlineBefore(AllocationStatus.PENDING_PICKUP, Instant.now());
        if (!overdue.isEmpty()) {
            log.info("Found {} overdue allocation(s)", overdue.size());
        }
        overdue.forEach(matchingService::releaseIfOverdue);
    }
}
