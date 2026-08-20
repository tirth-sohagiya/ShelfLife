package com.donationmatch.donation.repository;

import com.donationmatch.donation.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LotRepository extends JpaRepository<Lot, UUID> {

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE lots SET quantity_available = quantity_available - :qty, " +
            "status = CASE WHEN quantity_available - :qty <= 0 THEN 'DEPLETED' ELSE status END " +
            "WHERE id = :lotId",
            nativeQuery = true)
    void applyAllocation(@Param("lotId") UUID lotId, @Param("qty") Integer qty);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE lots SET status = 'EXPIRED' WHERE expiry_date < now() AND status = 'ACTIVE'",
            nativeQuery = true)
    int expireStaleLots();

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE lots SET quantity_available = quantity_available + :qty, " +
            "status = CASE WHEN status = 'DEPLETED' THEN 'ACTIVE' ELSE status END " +
            "WHERE id = :lotId",
            nativeQuery = true)
    void releaseAllocation(@Param("lotId") UUID lotId, @Param("qty") Integer qty);
}