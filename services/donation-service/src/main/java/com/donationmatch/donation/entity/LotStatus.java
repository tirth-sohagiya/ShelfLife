package com.donationmatch.donation.entity;

public enum LotStatus {
    ACTIVE,   // has quantity_available > 0 and not expired
    EXPIRED,  // past expiry_date, no longer eligible for matching
    DEPLETED  // quantity_available reached 0 (fully allocated/picked up)
}
