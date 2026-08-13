package com.donationmatch.matching.event;

import com.donationmatch.matching.entity.ItemType;

import java.time.Instant;
import java.util.UUID;

public record DonationLotCreatedEvent(
        UUID lotId,
        ItemType itemType,
        Integer quantityAvailable,
        Instant expiryDate
) {}
