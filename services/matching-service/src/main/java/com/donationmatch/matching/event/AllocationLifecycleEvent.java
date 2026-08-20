package com.donationmatch.matching.event;

import java.util.UUID;

public record AllocationLifecycleEvent(
        UUID allocationId,
        UUID lotId,
        UUID requestId,
        Integer quantity,
        AllocationLifecycleEventType type
) {}
