package com.donationmatch.matching.repository;

import com.donationmatch.matching.entity.ItemType;
import com.donationmatch.matching.entity.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<Lot, UUID> {

    // Locks the row for the duration of the caller's transaction so
    // concurrent allocation attempts against the same lot serialize
    // instead of racing on stale data.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lot l WHERE l.id = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") UUID id);

    // FEFO-ordered candidates for a newly-arrived request; expired lots
    // are filtered out here rather than tracked as separate state.
    List<Lot> findByItemTypeAndExpiryDateAfterOrderByExpiryDateAsc(ItemType itemType, Instant now);
}
