package com.donationmatch.request.controller;

import com.donationmatch.request.dto.CreateRequestRequest;
import com.donationmatch.request.entity.Request;
import com.donationmatch.request.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    public ResponseEntity<Request> createRequest(@Valid @RequestBody CreateRequestRequest request) {
        Request created = requestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public Page<Request> getAllRequests(Pageable pageable) {
        return requestService.getAllRequests(pageable);
    }

    @GetMapping("/{id}")
    public Request getRequestById(@PathVariable UUID id) {
        return requestService.getRequestById(id);
    }
}
