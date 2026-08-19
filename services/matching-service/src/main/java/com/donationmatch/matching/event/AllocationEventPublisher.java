package com.donationmatch.matching.event;

import com.donationmatch.matching.entity.Allocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AllocationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AllocationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishAllocationCreated(Allocation allocation) {
        AllocationCreatedEvent event = new AllocationCreatedEvent(
                allocation.getId(),
                allocation.getLotId(),
                allocation.getRequestId(),
                allocation.getQuantity()
        );
        kafkaTemplate.send("allocation-created", allocation.getId().toString(), event);
        log.info("Published AllocationCreatedEvent {}", allocation.getId());
    }
}
