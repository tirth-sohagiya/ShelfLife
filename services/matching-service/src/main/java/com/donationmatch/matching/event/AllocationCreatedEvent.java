package com.donationmatch.matching.event;

import java.util.UUID;

public record AllocationCreatedEvent(
        UUID allocationId,
        UUID lotId,
        UUID requestId,
        Integer quantity
) {}
