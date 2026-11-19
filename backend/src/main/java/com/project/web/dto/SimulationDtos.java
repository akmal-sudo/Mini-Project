package com.project.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class SimulationDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationStartRequest {
        private Double eventsPerSec;
        private Long seed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabRunRequest {
        @NotBlank(message = "Target SKU is required")
        private String sku;

        @Min(value = 1, message = "Initial stock must be at least 1")
        private int initialStock;

        @Min(value = 1, message = "Thread count must be at least 1")
        private int threadCount;

        @Min(value = 1, message = "Orders per thread must be at least 1")
        private int ordersPerThread;

        @NotBlank(message = "Concurrency mode is required (NONE, LOCK, DB_ONLY, OPTIMISTIC)")
        private String concurrencyMode;

        private long artificialDelayMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkerPoolResizeRequest {
        @Min(value = 1, message = "Worker pool size must be between 1 and 64")
        private int workerPoolSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConcurrencyModeChangeRequest {
        @NotBlank(message = "Mode is required")
        private String mode;
    }
}
