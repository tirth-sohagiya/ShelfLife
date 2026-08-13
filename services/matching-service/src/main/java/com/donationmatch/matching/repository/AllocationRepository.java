package com.donationmatch.matching.repository;

import com.donationmatch.matching.entity.Allocation;
import com.donationmatch.matching.entity.AllocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    // Quantity currently held against this lot/request by allocations in
    // the given statuses. COALESCE avoids a NULL result when there are no
    // matching rows.
    @Query("SELECT COALESCE(SUM(a.quantity), 0) FROM Allocation a " +
            "WHERE a.lotId = :lotId AND a.status IN :statuses")
    Integer sumActiveQuantityByLotId(@Param("lotId") UUID lotId,
                                      @Param("statuses") List<AllocationStatus> statuses);

    @Query("SELECT COALESCE(SUM(a.quantity), 0) FROM Allocation a " +
            "WHERE a.requestId = :requestId AND a.status IN :statuses")
    Integer sumActiveQuantityByRequestId(@Param("requestId") UUID requestId,
                                          @Param("statuses") List<AllocationStatus> statuses);
}
