package com.project.web.controller;

import com.project.dbms.entity.Product;
import com.project.dbms.entity.Vendor;
import com.project.dbms.entity.VendorCatalogItem;
import com.project.dbms.repository.ProductRepository;
import com.project.dbms.repository.VendorCatalogRepository;
import com.project.dbms.repository.VendorRepository;
import com.project.web.dto.CatalogDtos.CatalogProductDto;
import com.project.web.dto.CatalogDtos.VendorCatalogDto;
import com.project.web.dto.CatalogDtos.VendorCatalogItemDto;
import com.project.web.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vendor side of the system.
 *  - /api/vendor/catalog : the calling vendor's OWN catalog + OWN stock (vendor DB)
 *  - /api/catalog        : the manager's master catalog (names/prices only, never manager stock),
 *                          so a vendor can order products that are not in its own catalog
 */
@RestController
@RequestMapping("/api")
public class VendorCatalogController {

    private final VendorCatalogRepository vendorCatalogRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;

    public VendorCatalogController(
            VendorCatalogRepository vendorCatalogRepository,
            VendorRepository vendorRepository,
            ProductRepository productRepository) {
        this.vendorCatalogRepository = vendorCatalogRepository;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/vendor/catalog")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<VendorCatalogDto> myCatalog(@AuthenticationPrincipal UserPrincipal principal) {
        Integer vendorId = principal.getVendorId();
        String vendorName = vendorRepository.findById(vendorId).map(Vendor::getName).orElse("Vendor #" + vendorId);

        List<VendorCatalogItemDto> items = vendorCatalogRepository.findByVendorIdOrderBySkuAsc(vendorId).stream()
                .map(c -> VendorCatalogItemDto.builder()
                        .sku(c.getSku())
                        .name(c.getName())
                        .category(c.getCategory())
                        .price(c.getPrice())
                        .quantity(c.getQuantity())
                        .source(c.getSource())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(VendorCatalogDto.builder()
                .vendorId(vendorId)
                .vendorName(vendorName)
                .items(items)
                .build());
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<CatalogProductDto>> managerCatalog(@AuthenticationPrincipal UserPrincipal principal) {
        Set<String> mine = new HashSet<>();
        if (principal != null && "VENDOR".equals(principal.getRole()) && principal.getVendorId() != null) {
            vendorCatalogRepository.findByVendorIdOrderBySkuAsc(principal.getVendorId())
                    .forEach((VendorCatalogItem c) -> mine.add(c.getSku()));
        }

        List<CatalogProductDto> catalog = productRepository.findByActiveTrue().stream()
                .sorted((a, b) -> a.getSku().compareTo(b.getSku()))
                .map((Product p) -> CatalogProductDto.builder()
                        .sku(p.getSku())
                        .name(p.getName())
                        .category(p.getCategory())
                        .price(p.getPrice())
                        .inMyCatalog(mine.contains(p.getSku()))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(catalog);
    }
}
