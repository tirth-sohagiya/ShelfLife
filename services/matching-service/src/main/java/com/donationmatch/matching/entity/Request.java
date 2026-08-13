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
 * Local, eventually-consistent read model of a shelter request, populated
 * from RequestCreatedEvent messages. Fulfilled quantity is derived from
 * this service's own Allocation rows rather than stored here directly.
 */
@Entity
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
public class Request {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID shelterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private Integer quantityRequested;

    @Column(nullable = false)
    private Instant createdAt;
}
