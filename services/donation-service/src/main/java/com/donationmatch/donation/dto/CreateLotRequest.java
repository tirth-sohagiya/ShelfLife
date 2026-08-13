package com.donationmatch.donation.dto;

import com.donationmatch.donation.entity.ItemType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CreateLotRequest(
        @NotNull UUID donorId,
        @NotNull ItemType itemType,
        @NotNull @Positive Integer quantityTotal,
        @NotNull @Future Instant expiryDate
) {
}
