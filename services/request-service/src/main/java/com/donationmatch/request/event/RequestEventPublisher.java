package com.donationmatch.request.event;

import com.donationmatch.request.entity.Request;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class RequestEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RequestEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishRequestCreated(Request request) {
        RequestCreatedEvent event = new RequestCreatedEvent(
                request.getId(),
                request.getShelterId(),
                request.getItemType(),
                request.getQuantityRequested(),
                request.getCreatedAt()
        );
        kafkaTemplate.send("request-created", request.getId().toString(), event);
    }
}
