package com.donationmatch.donation.listener;

import com.donationmatch.donation.event.AllocationLifecycleEvent;
import com.donationmatch.donation.repository.LotRepository;
import com.donationmatch.donation.repository.ProcessedAllocationRepository;
import com.donationmatch.donation.repository.ProcessedAllocationReleaseRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AllocationLifecycleListener {

    private final LotRepository lotRepository;
    private final ProcessedAllocationRepository processedAllocationRepository;
    private final ProcessedAllocationReleaseRepository processedAllocationReleaseRepository;
    private final MeterRegistry meterRegistry;

    public AllocationLifecycleListener(LotRepository lotRepository,
                                        ProcessedAllocationRepository processedAllocationRepository,
                                        ProcessedAllocationReleaseRepository processedAllocationReleaseRepository,
                                        MeterRegistry meterRegistry) {
        this.lotRepository = lotRepository;
        this.processedAllocationRepository = processedAllocationRepository;
        this.processedAllocationReleaseRepository = processedAllocationReleaseRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    @KafkaListener(topics = "allocation-lifecycle", containerFactory = "allocationLifecycleListenerFactory")
    public void handleAllocationLifecycleEvent(AllocationLifecycleEvent event) {
        switch (event.type()) {
            case CREATED -> handleCreated(event);
            case EXPIRED -> handleExpired(event);
        }
    }

    private void handleCreated(AllocationLifecycleEvent event) {
        if (processedAllocationRepository.markProcessed(event.allocationId()) == 0) {
            log.warn("Duplicate delivery of allocation {} - already applied, skipping", event.allocationId());
            meterRegistry.counter("donation.allocation.events", "type", "created", "outcome", "duplicate").increment();
            return;
        }
        lotRepository.applyAllocation(event.lotId(), event.quantity());
        log.info("Applied allocation {} to lot {} - {} units", event.allocationId(), event.lotId(), event.quantity());
        meterRegistry.counter("donation.allocation.events", "type", "created", "outcome", "applied").increment();
    }

    private void handleExpired(AllocationLifecycleEvent event) {
        if (processedAllocationReleaseRepository.markProcessed(event.allocationId()) == 0) {
            log.warn("Duplicate delivery of expired allocation {} - already released, skipping", event.allocationId());
            meterRegistry.counter("donation.allocation.events", "type", "expired", "outcome", "duplicate").increment();
            return;
        }
        lotRepository.releaseAllocation(event.lotId(), event.quantity());
        log.info("Released allocation {} back to lot {} - {} units", event.allocationId(), event.lotId(), event.quantity());
        meterRegistry.counter("donation.allocation.events", "type", "expired", "outcome", "applied").increment();
    }
}
