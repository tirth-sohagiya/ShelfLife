package com.donationmatch.matching.repository;

import com.donationmatch.matching.entity.ItemType;
import com.donationmatch.matching.entity.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findByIdForUpdate(@Param("id") UUID id);

    // Oldest-first candidates for a newly-arrived lot.
    List<Request> findByItemTypeOrderByCreatedAtAsc(ItemType itemType);
}
