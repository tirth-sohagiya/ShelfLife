package com.donationmatch.request.service;

import com.donationmatch.request.dto.CreateRequestRequest;
import com.donationmatch.request.entity.Request;
import com.donationmatch.request.entity.RequestStatus;
import com.donationmatch.request.event.RequestEventPublisher;
import com.donationmatch.request.repository.RequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestEventPublisher publisher;

    public RequestService(RequestRepository requestRepository, RequestEventPublisher publisher) {
        this.requestRepository = requestRepository;
        this.publisher = publisher;
    }

    public Request createRequest(CreateRequestRequest request) {
        Request r = new Request();
        r.setShelterId(request.shelterId());
        r.setItemType(request.itemType());
        r.setQuantityRequested(request.quantityRequested());
        r.setQuantityFulfilled(0);
        r.setStatus(RequestStatus.OPEN);
        r.setCreatedAt(Instant.now());

        Request saved = requestRepository.save(r);
        log.info("Created request {} - shelter {}, {} units of {}",
                saved.getId(), saved.getShelterId(), saved.getQuantityRequested(), saved.getItemType());

        publisher.publishRequestCreated(saved);
        return saved;
    }

    public List<Request> getAllRequests() {
        return requestRepository.findAll();
    }

    public Request getRequestById(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + id));
    }
}
