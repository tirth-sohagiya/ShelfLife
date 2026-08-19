package com.donationmatch.matching.controller;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.service.AllocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping("/{id}/confirm")
    public Allocation confirmPickup(@PathVariable UUID id) {
        return allocationService.confirmPickup(id);
    }

    @GetMapping
    public List<Allocation> getAllocations(@RequestParam UUID lotId) {
        return allocationService.getAllocationsForLot(lotId);
    }
}
