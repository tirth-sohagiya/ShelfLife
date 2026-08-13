package com.donationmatch.request.dto;

import com.donationmatch.request.entity.ItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateRequestRequest(
        @NotNull UUID shelterId,
        @NotNull ItemType itemType,
        @NotNull @Positive Integer quantityRequested
) {
}
