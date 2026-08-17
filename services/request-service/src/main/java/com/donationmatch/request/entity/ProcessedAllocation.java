package com.donationmatch.request.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "processed_allocations")
public class ProcessedAllocation {

    @Id
    private UUID allocationId;

    public ProcessedAllocation() {
    }

    public ProcessedAllocation(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }
}
