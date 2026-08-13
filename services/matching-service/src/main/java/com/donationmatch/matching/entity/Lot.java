package com.donationmatch.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Local, eventually-consistent read model of a donation lot, populated
 * from DonationLotCreatedEvent messages. donation-service remains the
 * source of truth.
 */
@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    // Not auto-generated - must match the id assigned by donation-service.
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private Integer quantityAvailable;

    @Column(nullable = false)
    private Instant expiryDate;
}
