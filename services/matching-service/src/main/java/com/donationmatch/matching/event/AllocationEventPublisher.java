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
        publish(allocation, AllocationLifecycleEventType.CREATED);
    }

    public void publishAllocationExpired(Allocation allocation) {
        publish(allocation, AllocationLifecycleEventType.EXPIRED);
    }

    private void publish(Allocation allocation, AllocationLifecycleEventType type) {
        AllocationLifecycleEvent event = new AllocationLifecycleEvent(
                allocation.getId(),
                allocation.getLotId(),
                allocation.getRequestId(),
                allocation.getQuantity(),
                type
        );
        kafkaTemplate.send("allocation-lifecycle", allocation.getId().toString(), event);
        log.info("Published AllocationLifecycleEvent {} ({})", allocation.getId(), type);
    }
}
