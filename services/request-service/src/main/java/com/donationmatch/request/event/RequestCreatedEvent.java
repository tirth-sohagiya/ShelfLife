package com.donationmatch.request.event;

import com.donationmatch.request.entity.ItemType;

import java.time.Instant;
import java.util.UUID;

public record RequestCreatedEvent(
        UUID requestId,
        UUID shelterId,
        ItemType itemType,
        Integer quantityRequested,
        Instant createdAt
) {}
