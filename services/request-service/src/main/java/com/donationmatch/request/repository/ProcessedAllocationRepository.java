package com.donationmatch.request.repository;

import com.donationmatch.request.entity.ProcessedAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedAllocationRepository extends JpaRepository<ProcessedAllocation, UUID> {

    @Modifying
    @Query(value = "INSERT INTO processed_allocations (allocation_id) VALUES (:allocationId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int markProcessed(@Param("allocationId") UUID allocationId);
}
