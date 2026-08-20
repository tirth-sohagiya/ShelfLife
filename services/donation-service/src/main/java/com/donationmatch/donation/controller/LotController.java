package com.donationmatch.donation.controller;

import com.donationmatch.donation.dto.CreateLotRequest;
import com.donationmatch.donation.entity.Lot;
import com.donationmatch.donation.service.LotService;
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
@RequestMapping("/lots")
public class LotController {

    private final LotService lotService;

    public LotController(LotService lotService) {
        this.lotService = lotService;
    }

    @PostMapping
    public ResponseEntity<Lot> createLot(@Valid @RequestBody CreateLotRequest request) {
        Lot created = lotService.createLot(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public Page<Lot> getAllLots(Pageable pageable) {
        return lotService.getAllLots(pageable);
    }

    @GetMapping("/{id}")
    public Lot getLotById(@PathVariable UUID id) {
        return lotService.getLotById(id);
    }
}
