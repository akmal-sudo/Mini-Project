package com.project.os;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ExecutionResult {
    private final boolean success;
    private final String eventId;
    private final String sku;
    private final String eventType;
    private final int previousQuantity;
    private final int newQuantity;
    private final String status; // COMPLETED, REJECTED, FAILED
    private final String message;
}
