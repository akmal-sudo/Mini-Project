package com.project.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class CatalogDtos {

    /** One product of the MANAGER catalog as a vendor sees it (no manager stock numbers). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogProductDto {
        private String sku;
        private String name;
        private String category;
        private BigDecimal price;
        /** true when this SKU is already in the calling vendor's own catalog */
        private boolean inMyCatalog;
    }

    /** One product of a vendor's OWN catalog with the vendor's own stock count. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorCatalogItemDto {
        private String sku;
        private String name;
        private String category;
        private BigDecimal price;
        private int quantity;
        private String source; // OWN | MANAGER_CATALOG
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorCatalogDto {
        private Integer vendorId;
        private String vendorName;
        private List<VendorCatalogItemDto> items;
    }
}
