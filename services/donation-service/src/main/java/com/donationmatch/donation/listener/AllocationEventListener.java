package com.donationmatch.donation.listener;

import com.donationmatch.donation.event.AllocationCreatedEvent;
import com.donationmatch.donation.repository.LotRepository;
import com.donationmatch.donation.repository.ProcessedAllocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AllocationEventListener {

    private final LotRepository lotRepository;
    private final ProcessedAllocationRepository processedAllocationRepository;

    public AllocationEventListener(LotRepository lotRepository,
                                    ProcessedAllocationRepository processedAllocationRepository) {
        this.lotRepository = lotRepository;
        this.processedAllocationRepository = processedAllocationRepository;
    }

    @Transactional
    @KafkaListener(topics = "allocation-created", containerFactory = "allocationListenerFactory")
    public void handleAllocationCreated(AllocationCreatedEvent event) {
        if (processedAllocationRepository.markProcessed(event.allocationId()) == 0) {
            log.warn("Duplicate delivery of allocation {} - already applied, skipping", event.allocationId());
            return;
        }
        lotRepository.applyAllocation(event.lotId(), event.quantity());
        log.info("Applied allocation {} to lot {} - {} units", event.allocationId(), event.lotId(), event.quantity());
    }
}
