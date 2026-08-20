package com.donationmatch.request.listener;

import com.donationmatch.request.event.AllocationLifecycleEvent;
import com.donationmatch.request.repository.ProcessedAllocationRepository;
import com.donationmatch.request.repository.ProcessedAllocationReleaseRepository;
import com.donationmatch.request.repository.RequestRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AllocationLifecycleListener {

    private final RequestRepository requestRepository;
    private final ProcessedAllocationRepository processedAllocationRepository;
    private final ProcessedAllocationReleaseRepository processedAllocationReleaseRepository;
    private final MeterRegistry meterRegistry;

    public AllocationLifecycleListener(RequestRepository requestRepository,
                                        ProcessedAllocationRepository processedAllocationRepository,
                                        ProcessedAllocationReleaseRepository processedAllocationReleaseRepository,
                                        MeterRegistry meterRegistry) {
        this.requestRepository = requestRepository;
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
            meterRegistry.counter("request.allocation.events", "type", "created", "outcome", "duplicate").increment();
            return;
        }
        requestRepository.applyAllocation(event.requestId(), event.quantity());
        log.info("Applied allocation {} to request {} - {} units", event.allocationId(), event.requestId(), event.quantity());
        meterRegistry.counter("request.allocation.events", "type", "created", "outcome", "applied").increment();
    }

    private void handleExpired(AllocationLifecycleEvent event) {
        if (processedAllocationReleaseRepository.markProcessed(event.allocationId()) == 0) {
            log.warn("Duplicate delivery of expired allocation {} - already released, skipping", event.allocationId());
            meterRegistry.counter("request.allocation.events", "type", "expired", "outcome", "duplicate").increment();
            return;
        }
        requestRepository.releaseAllocation(event.requestId(), event.quantity());
        log.info("Released allocation {} from request {} - {} units", event.allocationId(), event.requestId(), event.quantity());
        meterRegistry.counter("request.allocation.events", "type", "expired", "outcome", "applied").increment();
    }
}
