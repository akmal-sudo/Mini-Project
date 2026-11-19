package com.project.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

public class InventoryDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustStockRequest {
        @NotNull(message = "Adjustment delta is required")
        private Integer delta;

        @NotBlank(message = "Reason is required (e.g., ADMIN_ADJUST)")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplenishRequest {
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100000, message = "Quantity must be at most 100000")
        private Integer quantity;

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryDto {
        private String sku;
        private String name;
        private String category;
        private BigDecimal price;
        private int quantity;
        private int initialQuantity;
        private int reorderThreshold;
        private int reorderQuantity;
        private String status; // OK, LOW, OUT_OF_STOCK
        private long version;
        private Instant updatedAt;
    }
}
