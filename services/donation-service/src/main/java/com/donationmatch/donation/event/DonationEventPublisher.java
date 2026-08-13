package com.donationmatch.donation.event;

import com.donationmatch.donation.entity.Lot;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DonationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DonationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishLotCreated(Lot lot) {
        DonationLotCreatedEvent event = new DonationLotCreatedEvent(
                lot.getId(),
                lot.getItemType(),
                lot.getQuantityAvailable(),
                lot.getExpiryDate()
        );
        kafkaTemplate.send("donation-lot-created", lot.getId().toString(), event);
    }
}
