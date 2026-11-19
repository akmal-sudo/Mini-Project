package com.project.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class ReorderDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateReorderRequest {
        @NotBlank(message = "SKU is required")
        private String sku;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        private String severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelReorderRequest {
        @NotBlank(message = "Cancellation reason is required")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReorderDto {
        private Long reorderId;
        private String sku;
        private Integer vendorId;
        private int quantity;
        private BigDecimal priorityScore;
        private String severity;
        private String status;
        private Long alertId;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant deliveredAt;
    }
}
