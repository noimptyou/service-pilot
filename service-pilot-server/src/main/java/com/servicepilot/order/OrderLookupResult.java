package com.servicepilot.order;

public record OrderLookupResult(
        boolean found,
        String orderNumber,
        String productName,
        String status,
        String trackingNumber,
        String message
) {
}
