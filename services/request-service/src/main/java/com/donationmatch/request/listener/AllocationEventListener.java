package com.donationmatch.request.listener;

import com.donationmatch.request.event.AllocationCreatedEvent;
import com.donationmatch.request.repository.ProcessedAllocationRepository;
import com.donationmatch.request.repository.RequestRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
            return;
        }
        requestRepository.applyAllocation(event.requestId(), event.quantity());
    }
}
