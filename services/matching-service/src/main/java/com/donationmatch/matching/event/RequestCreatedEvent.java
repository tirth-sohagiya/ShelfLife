package com.donationmatch.matching.event;

import com.donationmatch.matching.entity.ItemType;

import java.time.Instant;
import java.util.UUID;

public record RequestCreatedEvent(
        UUID requestId,
        UUID shelterId,
        ItemType itemType,
        Integer quantityRequested,
        Instant createdAt
) {}
