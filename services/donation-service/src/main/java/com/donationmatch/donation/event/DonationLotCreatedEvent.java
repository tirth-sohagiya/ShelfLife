package com.donationmatch.donation.event;

import com.donationmatch.donation.entity.ItemType;

import java.time.Instant;
import java.util.UUID;

public record DonationLotCreatedEvent(
        UUID lotId,
        ItemType itemType,
        Integer quantityAvailable,
        Instant expiryDate
) {}

