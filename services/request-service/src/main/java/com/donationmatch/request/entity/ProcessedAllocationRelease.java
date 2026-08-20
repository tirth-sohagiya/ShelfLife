package com.donationmatch.request.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "processed_allocation_releases")
public class ProcessedAllocationRelease {

    @Id
    private UUID allocationId;

    public ProcessedAllocationRelease() {
    }

    public ProcessedAllocationRelease(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }
}
