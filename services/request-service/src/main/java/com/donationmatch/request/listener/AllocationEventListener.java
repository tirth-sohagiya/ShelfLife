package com.donationmatch.request.listener;

import com.donationmatch.request.event.AllocationCreatedEvent;
import com.donationmatch.request.repository.ProcessedAllocationRepository;
import com.donationmatch.request.repository.RequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AllocationEventListener {

    private final RequestRepository requestRepository;
    private final ProcessedAllocationRepository processedAllocationRepository;

    public AllocationEventListener(RequestRepository requestRepository,
                                    ProcessedAllocationRepository processedAllocationRepository) {
        this.requestRepository = requestRepository;
        this.processedAllocationRepository = processedAllocationRepository;
    }

    @Transactional
    @KafkaListener(topics = "allocation-created", containerFactory = "allocationListenerFactory")
    public void handleAllocationCreated(AllocationCreatedEvent event) {
        if (processedAllocationRepository.markProcessed(event.allocationId()) == 0) {
            log.warn("Duplicate delivery of allocation {} - already applied, skipping", event.allocationId());
            return;
        }
        requestRepository.applyAllocation(event.requestId(), event.quantity());
        log.info("Applied allocation {} to request {} - {} units", event.allocationId(), event.requestId(), event.quantity());
    }
}
