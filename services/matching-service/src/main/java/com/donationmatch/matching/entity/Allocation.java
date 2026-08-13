package com.donationmatch.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A reservation of some quantity of a Lot against a Request, with its own
 * pickup deadline. A Request can accumulate several Allocations over time
 * as it draws from multiple lots.
 */
@Entity
@Table(name = "allocations")
@Getter
@Setter
@NoArgsConstructor
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID lotId;

    @Column(nullable = false)
    private UUID requestId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllocationStatus status;

    @Column(nullable = false)
    private Instant pickupDeadline;

    @Column(nullable = false)
    private Instant createdAt;
}
