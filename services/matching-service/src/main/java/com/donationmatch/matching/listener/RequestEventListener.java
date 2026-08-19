package com.donationmatch.matching.listener;

import com.donationmatch.matching.entity.Request;
import com.donationmatch.matching.event.RequestCreatedEvent;
import com.donationmatch.matching.repository.RequestRepository;
import com.donationmatch.matching.service.MatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RequestEventListener {

    private final RequestRepository requestRepository;
    private final MatchingService matchingService;

    public RequestEventListener(RequestRepository requestRepository, MatchingService matchingService) {
        this.requestRepository = requestRepository;
        this.matchingService = matchingService;
    }

    @KafkaListener(topics = "request-created", containerFactory = "requestListenerFactory")
    public void handleRequestCreated(RequestCreatedEvent event) {
        Request request = new Request();
        request.setId(event.requestId());
        request.setShelterId(event.shelterId());
        request.setItemType(event.itemType());
        request.setQuantityRequested(event.quantityRequested());
        request.setCreatedAt(event.createdAt());
        requestRepository.save(request);
        log.info("Received request {} - {} units of {}", request.getId(), request.getQuantityRequested(), request.getItemType());

        matchingService.matchNewRequest(request);
    }
}
