package com.donationmatch.donation.repository;

import com.donationmatch.donation.entity.ProcessedAllocationRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedAllocationReleaseRepository extends JpaRepository<ProcessedAllocationRelease, UUID> {

    @Modifying
    @Query(value = "INSERT INTO processed_allocation_releases (allocation_id) VALUES (:allocationId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int markProcessed(@Param("allocationId") UUID allocationId);
}
