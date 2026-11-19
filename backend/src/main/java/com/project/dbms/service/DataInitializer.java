package com.project.dbms.service;

import com.project.dbms.entity.Inventory;
import com.project.dbms.entity.Product;
import com.project.dbms.entity.User;
import com.project.dbms.entity.VendorCatalogItem;
import com.project.dbms.entity.Vendor;
import com.project.dbms.repository.InventoryRepository;
import com.project.dbms.repository.ProductRepository;
import com.project.dbms.repository.UserRepository;
import com.project.dbms.repository.VendorCatalogRepository;
import com.project.dbms.repository.VendorRepository;
import com.project.dsa.DsaEngine;
import com.project.dsa.InventoryCache;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Initializes seed data upon application startup, into two separate databases:
 *  MANAGER DB (schema public): 50 master-catalog products, each holding MANAGER_BASE_STOCK units. No vendor data.
 *  VENDOR  DB (schema vendor): 3 vendors, and for each vendor its own catalog with its own stock counts.
 * Plus the demo users (Admin, Warehouse Manager, 3 Vendors) with BCrypt-hashed passwords.
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** Every SKU in the manager DB starts with this many units. */
    static final int MANAGER_BASE_STOCK = 1000;

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final VendorCatalogRepository vendorCatalogRepository;
    private final DsaEngine dsaEngine;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public DataInitializer(
            VendorRepository vendorRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            VendorCatalogRepository vendorCatalogRepository,
            DsaEngine dsaEngine) {
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.vendorCatalogRepository = vendorCatalogRepository;
        this.dsaEngine = dsaEngine;
    }

    @PostConstruct
    @Transactional
    public void seedInitialData() {
        if (vendorRepository.count() > 0) {
            log.info("Database already seeded. Ensuring all 3 vendors, users, and constant passwords...");
            ensureDemoUsersAndVendors();
            inventoryRepository.findAll().forEach(inv ->
                    dsaEngine.getInventoryCache().put(inv.getSku(), inv.getQuantity(), inv.getReorderThreshold(), inv.getReorderQuantity(), inv.getVersion())
            );
            return;
        }

        log.info("Seeding manager DB (50 SKUs x 1000 units) and vendor DB (3 vendors + their catalogs)...");

        // 1. Seed 3 Vendors
        Vendor v1 = vendorRepository.save(Vendor.builder()
                .name("FreshFarms Logistics")
                .contactEmail("orders@freshfarms.com")
                .phone("+1-555-0101")
                .leadTimeDays(2)
                .build());

        Vendor v2 = vendorRepository.save(Vendor.builder()
                .name("Metro Wholesale Goods")
                .contactEmail("supply@metrowholesale.com")
                .phone("+1-555-0202")
                .leadTimeDays(3)
                .build());

        Vendor v3 = vendorRepository.save(Vendor.builder()
                .name("Aura Personal & Home Care")
                .contactEmail("dispatch@auracare.com")
                .phone("+1-555-0303")
                .leadTimeDays(4)
                .build());

        // 2. Seed Demo Users with BCrypt hashes (strength 10) - constant passwords
        userRepository.save(User.builder()
                .username("admin")
                .email("admin@quickcommerce.internal")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role("ADMIN")
                .mustChangePassword(false)
                .enabled(true)
                .build());

        userRepository.save(User.builder()
                .username("warehouse1")
                .email("warehouse1@quickcommerce.internal")
                .passwordHash(passwordEncoder.encode("Wh@12345"))
                .role("WAREHOUSE_MANAGER")
                .mustChangePassword(false)
                .enabled(true)
                .build());

        userRepository.save(User.builder()
                .username("vendor1")
                .email("vendor1@freshfarms.com")
                .passwordHash(passwordEncoder.encode("Vendor@123"))
                .role("VENDOR")
                .vendorId(v1.getVendorId())
                .mustChangePassword(false)
                .enabled(true)
                .build());

        userRepository.save(User.builder()
                .username("vendor2")
                .email("vendor2@metrowholesale.com")
                .passwordHash(passwordEncoder.encode("Vendor@123"))
                .role("VENDOR")
                .vendorId(v2.getVendorId())
                .mustChangePassword(false)
                .enabled(true)
                .build());

        userRepository.save(User.builder()
                .username("vendor3")
                .email("vendor3@auracare.com")
                .passwordHash(passwordEncoder.encode("Vendor@123"))
                .role("VENDOR")
                .vendorId(v3.getVendorId())
                .mustChangePassword(false)
                .enabled(true)
                .build());

        // 3. Seed 50 Quick-Commerce Products with Inventory
        List<Product> products = new ArrayList<>(50);
        List<Inventory> inventories = new ArrayList<>(50);

        String[][] sampleCatalog = {
                // SKU, Name, Category, Price, VendorIndex, VendorStartingStock, ManagerThreshold, ManagerReorderQty
                {"SKU-MILK-01", "Farm Fresh Whole Milk 1L", "Dairy", "3.49", "1", "120", "25", "100"},
                {"SKU-EGGS-02", "Grade A Organic Eggs 12pk", "Dairy", "4.99", "1", "90", "20", "80"},
                {"SKU-BREAD-03", "Artisan Sourdough Loaf", "Bakery", "4.29", "2", "60", "15", "50"},
                {"SKU-BANANA-04", "Cavendish Bananas 1kg", "Produce", "1.99", "1", "150", "30", "100"},
                {"SKU-APPLE-05", "Honeycrisp Apples 1kg", "Produce", "3.99", "1", "80", "20", "60"},
                {"SKU-CHICKEN-06", "Boneless Chicken Breast 500g", "Meat", "6.99", "1", "70", "15", "50"},
                {"SKU-BUTTER-07", "Unsalted Sweet Cream Butter", "Dairy", "4.49", "1", "85", "20", "60"},
                {"SKU-YOGURT-08", "Greek Vanilla Yogurt 500g", "Dairy", "3.79", "1", "65", "15", "50"},
                {"SKU-CHEESE-09", "Sharp Cheddar Block 250g", "Dairy", "5.19", "1", "55", "12", "40"},
                {"SKU-AVOCADO-10", "Hass Avocados 4pk", "Produce", "4.99", "1", "75", "18", "60"},
                {"SKU-COFFEE-11", "Medium Roast Ground Coffee 340g", "Beverages", "8.99", "2", "50", "10", "40"},
                {"SKU-TEA-12", "English Breakfast Tea 50 bags", "Beverages", "4.49", "2", "60", "12", "50"},
                {"SKU-OJ-13", "Pulp-Free Pure Orange Juice 1.5L", "Beverages", "4.99", "1", "70", "15", "50"},
                {"SKU-WATER-14", "Sparkling Spring Water 12pk", "Beverages", "6.49", "2", "110", "25", "80"},
                {"SKU-COLA-15", "Classic Cola 6-pack cans", "Beverages", "5.29", "2", "95", "20", "70"},
                {"SKU-CHIPS-16", "Sea Salt Kettle Cooked Chips", "Snacks", "3.29", "2", "80", "15", "60"},
                {"SKU-NUTS-17", "Roasted Salted Almonds 200g", "Snacks", "5.99", "2", "65", "12", "50"},
                {"SKU-CHOCO-18", "72% Dark Chocolate Bar 100g", "Snacks", "2.99", "2", "90", "18", "70"},
                {"SKU-COOKIES-19", "Choco-Chip Soft Baked Cookies", "Snacks", "3.79", "2", "70", "15", "50"},
                {"SKU-POPCORN-20", "White Cheddar Popcorn 150g", "Snacks", "2.89", "2", "85", "20", "60"},
                {"SKU-RICE-21", "Jasmine Rice 2kg Bag", "Pantry", "6.49", "2", "90", "20", "60"},
                {"SKU-PASTA-22", "Italian Penne Rigate 500g", "Pantry", "1.99", "2", "140", "30", "100"},
                {"SKU-SAUCE-23", "Classic Marinara Pasta Sauce", "Pantry", "3.49", "2", "75", "18", "60"},
                {"SKU-OLIVEOIL-24", "Extra Virgin Olive Oil 750ml", "Pantry", "11.99", "2", "40", "10", "30"},
                {"SKU-OATMEAL-25", "Rolled Whole Grain Oats 1kg", "Pantry", "3.99", "2", "60", "15", "50"},
                {"SKU-CEREAL-26", "Honey Nut Toasted Cereal", "Pantry", "4.69", "2", "55", "12", "40"},
                {"SKU-SOUP-27", "Creamy Tomato Basil Soup", "Pantry", "2.99", "2", "80", "16", "60"},
                {"SKU-TUNA-28", "Wild Albacore Tuna in Water", "Pantry", "2.49", "2", "100", "20", "80"},
                {"SKU-FLOUR-29", "Unbleached All-Purpose Flour 2kg", "Pantry", "3.89", "2", "50", "10", "40"},
                {"SKU-SUGAR-30", "Pure Cane Granulated Sugar 1kg", "Pantry", "2.79", "2", "65", "15", "50"},
                {"SKU-SOAP-31", "Botanical Moisturizing Hand Soap", "Personal Care", "3.99", "3", "70", "15", "50"},
                {"SKU-SHAMPOO-32", "Daily Hydrating Shampoo 400ml", "Personal Care", "6.99", "3", "45", "10", "40"},
                {"SKU-CONDITIONER-33", "Silk Protein Conditioner 400ml", "Personal Care", "6.99", "3", "40", "10", "40"},
                {"SKU-BODYWASH-34", "Eucalyptus & Mint Body Wash", "Personal Care", "5.49", "3", "55", "12", "40"},
                {"SKU-TOOTHPASTE-35", "Whitening Fluoride Toothpaste", "Personal Care", "3.79", "3", "80", "18", "60"},
                {"SKU-DEODORANT-36", "Cedar & Cypress Natural Deodorant", "Personal Care", "5.99", "3", "60", "12", "45"},
                {"SKU-PAPER-37", "Ultra Soft Toilet Paper 12pk", "Household", "9.99", "3", "85", "20", "70"},
                {"SKU-TOWELS-38", "Absorbent Paper Towels 6pk", "Household", "8.49", "3", "75", "18", "60"},
                {"SKU-DISHSOAP-39", "Lemon Verbena Dish Soap 500ml", "Household", "3.29", "3", "90", "20", "60"},
                {"SKU-DETERGENT-40", "Concentrated Laundry Pods 42ct", "Household", "14.99", "3", "40", "10", "30"},
                {"SKU-TRASHBAGS-41", "Heavy Duty Drawstring Bags 40ct", "Household", "7.99", "3", "60", "15", "50"},
                {"SKU-DISINFECT-42", "Multi-Surface Cleaning Spray", "Household", "4.19", "3", "70", "15", "50"},
                {"SKU-SPONGES-43", "Non-Scratch Scrub Sponges 6pk", "Household", "4.49", "3", "65", "14", "50"},
                {"SKU-ALUMINUM-44", "Heavy Duty Foil 75ft", "Household", "5.29", "3", "55", "12", "40"},
                {"SKU-ZIPBAGS-45", "Gallon Storage Slider Bags 30ct", "Household", "4.69", "3", "70", "15", "50"},
                {"SKU-BATTERIES-46", "AA Alkaline Long-Life 8pk", "Electronics", "7.49", "2", "60", "12", "40"},
                {"SKU-LIGHTBULB-47", "LED Warm White Bulbs 4pk", "Electronics", "8.99", "2", "45", "10", "30"},
                {"SKU-CHARGER-48", "USB-C Fast Charging Cable 2m", "Electronics", "9.99", "2", "50", "10", "35"},
                {"SKU-EARBUDS-49", "True Wireless Earbuds Case", "Electronics", "24.99", "2", "30", "8", "25"},
                {"SKU-ICE-50", "Pure Filtered Ice Bag 2kg", "Frozen", "2.49", "1", "100", "25", "70"}
        };

        List<VendorCatalogItem> vendorItems = new ArrayList<>(50);

        for (String[] row : sampleCatalog) {
            String sku = row[0];
            String name = row[1];
            String category = row[2];
            BigDecimal price = new BigDecimal(row[3]);
            int vendorIndex = Integer.parseInt(row[4]);
            int vendorId = vendorIndex == 1 ? v1.getVendorId() : vendorIndex == 2 ? v2.getVendorId() : v3.getVendorId();
            int vendorStock = Integer.parseInt(row[5]);
            int threshold = Integer.parseInt(row[6]);
            int reorderQty = Integer.parseInt(row[7]);

            // ---- MANAGER DB: master catalog (no vendor) + stock of MANAGER_BASE_STOCK
            products.add(Product.builder()
                    .sku(sku)
                    .name(name)
                    .category(category)
                    .price(price)
                    .active(true)
                    .createdAt(Instant.now())
                    .build());

            inventories.add(Inventory.builder()
                    .sku(sku)
                    .quantity(MANAGER_BASE_STOCK)
                    .reorderThreshold(threshold)
                    .reorderQuantity(reorderQty)
                    .initialQuantity(MANAGER_BASE_STOCK)
                    .version(0L)
                    .updatedAt(Instant.now())
                    .build());

            // ---- VENDOR DB: the vendor's own catalog entry with the vendor's own stock count
            vendorItems.add(VendorCatalogItem.builder()
                    .vendorId(vendorId)
                    .sku(sku)
                    .name(name)
                    .category(category)
                    .price(price)
                    .quantity(vendorStock)
                    .source("OWN")
                    .createdAt(Instant.now())
                    .build());

            // Pre-warm DSA Cache (manager stock)
            dsaEngine.getInventoryCache().put(sku, MANAGER_BASE_STOCK, threshold, reorderQty, 0L);
        }

        productRepository.saveAll(products);
        inventoryRepository.saveAll(inventories);
        vendorCatalogRepository.saveAll(vendorItems);

        log.info("Seeded manager DB ({} SKUs x {} units) and vendor DB (3 vendors, {} catalog rows), plus 5 demo users.",
                products.size(), MANAGER_BASE_STOCK, vendorItems.size());
    }

    private void ensureDemoUsersAndVendors() {
        List<Vendor> vendors = vendorRepository.findAll();
        Vendor v1 = vendors.size() > 0 ? vendors.get(0) : null;
        Vendor v2 = vendors.size() > 1 ? vendors.get(1) : null;
        Vendor v3 = vendors.size() > 2 ? vendors.get(2) : null;

        // Reset all existing users mustChangePassword to false
        userRepository.findAll().forEach(u -> {
            boolean modified = false;
            if (u.isMustChangePassword()) {
                u.setMustChangePassword(false);
                modified = true;
            }
            if (u.getLockedUntil() != null) {
                u.setLockedUntil(null);
                u.setFailedAttempts(0);
                modified = true;
            }
            if (modified) {
                userRepository.save(u);
            }
        });

        // Ensure Admin
        userRepository.findByUsername("admin").ifPresentOrElse(
                u -> {
                    u.setPasswordHash(passwordEncoder.encode("Admin@123"));
                    u.setMustChangePassword(false);
                    userRepository.save(u);
                },
                () -> userRepository.save(User.builder()
                        .username("admin")
                        .email("admin@quickcommerce.internal")
                        .passwordHash(passwordEncoder.encode("Admin@123"))
                        .role("ADMIN")
                        .mustChangePassword(false)
                        .enabled(true)
                        .build())
        );

        // Ensure Warehouse Manager
        userRepository.findByUsername("warehouse1").ifPresentOrElse(
                u -> {
                    u.setPasswordHash(passwordEncoder.encode("Wh@12345"));
                    u.setMustChangePassword(false);
                    userRepository.save(u);
                },
                () -> userRepository.save(User.builder()
                        .username("warehouse1")
                        .email("warehouse1@quickcommerce.internal")
                        .passwordHash(passwordEncoder.encode("Wh@12345"))
                        .role("WAREHOUSE_MANAGER")
                        .mustChangePassword(false)
                        .enabled(true)
                        .build())
        );

        // Ensure Vendor 1
        if (v1 != null) {
            final int v1Id = v1.getVendorId();
            userRepository.findByUsername("vendor1").ifPresentOrElse(
                    u -> {
                        u.setPasswordHash(passwordEncoder.encode("Vendor@123"));
                        u.setVendorId(v1Id);
                        u.setMustChangePassword(false);
                        userRepository.save(u);
                    },
                    () -> userRepository.save(User.builder()
                            .username("vendor1")
                            .email("vendor1@freshfarms.com")
                            .passwordHash(passwordEncoder.encode("Vendor@123"))
                            .role("VENDOR")
                            .vendorId(v1Id)
                            .mustChangePassword(false)
                            .enabled(true)
                            .build())
            );
        }

        // Ensure Vendor 2
        if (v2 != null) {
            final int v2Id = v2.getVendorId();
            userRepository.findByUsername("vendor2").ifPresentOrElse(
                    u -> {
                        u.setPasswordHash(passwordEncoder.encode("Vendor@123"));
                        u.setVendorId(v2Id);
                        u.setMustChangePassword(false);
                        userRepository.save(u);
                    },
                    () -> userRepository.save(User.builder()
                            .username("vendor2")
                            .email("vendor2@metrowholesale.com")
                            .passwordHash(passwordEncoder.encode("Vendor@123"))
                            .role("VENDOR")
                            .vendorId(v2Id)
                            .mustChangePassword(false)
                            .enabled(true)
                            .build())
            );
        }

        // Ensure Vendor 3
        if (v3 != null) {
            final int v3Id = v3.getVendorId();
            userRepository.findByUsername("vendor3").ifPresentOrElse(
                    u -> {
                        u.setPasswordHash(passwordEncoder.encode("Vendor@123"));
                        u.setVendorId(v3Id);
                        u.setMustChangePassword(false);
                        userRepository.save(u);
                    },
                    () -> userRepository.save(User.builder()
                            .username("vendor3")
                            .email("vendor3@auracare.com")
                            .passwordHash(passwordEncoder.encode("Vendor@123"))
                            .role("VENDOR")
                            .vendorId(v3Id)
                            .mustChangePassword(false)
                            .enabled(true)
                            .build())
            );
        }
    }
}
