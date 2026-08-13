package com.donationmatch.request.entity;

public enum RequestStatus {
    OPEN,                 // quantityFulfilled == 0, nothing allocated yet
    PARTIALLY_FULFILLED,  // 0 < quantityFulfilled < quantityRequested
    FULFILLED,            // quantityFulfilled >= quantityRequested
    CANCELLED
}
