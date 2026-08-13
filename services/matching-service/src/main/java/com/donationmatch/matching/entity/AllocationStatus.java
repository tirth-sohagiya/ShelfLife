package com.donationmatch.matching.entity;

public enum AllocationStatus {
    PENDING_PICKUP, // reserved, waiting for shelter to collect before pickupDeadline
    CONFIRMED,      // shelter picked it up - final state
    EXPIRED         // pickupDeadline passed - triggers compensating release
}
