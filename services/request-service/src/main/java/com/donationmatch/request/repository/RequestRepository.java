package com.donationmatch.request.repository;

import com.donationmatch.request.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID> {

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE requests SET quantity_fulfilled = quantity_fulfilled + :qty, " +
            "status = CASE WHEN quantity_fulfilled + :qty >= quantity_requested THEN 'FULFILLED' ELSE 'PARTIALLY_FULFILLED' END " +
            "WHERE id = :requestId",
            nativeQuery = true)
    void applyAllocation(@Param("requestId") UUID requestId, @Param("qty") Integer qty);
}
