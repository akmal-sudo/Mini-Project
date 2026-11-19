package com.project.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.config.EmbeddedPostgresConfig;
import com.project.dbms.entity.User;
import com.project.dbms.repository.UserRepository;
import com.project.web.dto.AuthDtos.ChangePasswordRequest;
import com.project.web.dto.AuthDtos.RefreshTokenRequest;
import com.project.web.dto.InventoryDtos.AdjustStockRequest;
import com.project.web.dto.LoginRequest;
import com.project.web.dto.LoginResponse;
import com.project.web.security.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmbeddedPostgresConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("WEB")
class SecurityAndApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
    }

    @Test
    @DisplayName("Login with valid credentials returns JWT access and refresh tokens")
    void testSuccessfulLogin() throws Exception {
        LoginRequest req = new LoginRequest("admin", "Admin@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @Tag("AC-06")
    @DisplayName("AC-06: 5 consecutive failed login attempts lock account for 15 minutes")
    void testBruteForceAccountLockout() throws Exception {
        LoginRequest badReq = new LoginRequest("admin", "WrongPassword!");

        // 4 failed attempts should return 401 UNAUTHORIZED
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:problem:bad-credentials"));
        }

        // 5th failed attempt triggers 15-minute account lockout (403 FORBIDDEN)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem:account-locked"))
                .andExpect(jsonPath("$.detail").value("Account locked for 15 minutes after 5 consecutive failed login attempts"));

        // Subsequent attempt (even with CORRECT password) is locked out!
        LoginRequest correctReq = new LoginRequest("admin", "Admin@123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem:account-locked"));

        User user = userRepository.findByUsername("admin").orElseThrow();
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
    }

    @Test
    @Tag("AC-06")
    @DisplayName("AC-06: Token Bucket rate limiter returns 429 Too Many Requests after 100 requests in 1 min")
    void testRateLimiter429() throws Exception {
        // Send 100 successful requests
        for (int i = 0; i < 100; i++) {
            boolean acquired = rateLimiter.tryAcquire("127.0.0.1");
            assertTrue(acquired, "First 100 requests should be permitted");
        }

        // 101st request should be rejected by the filter with HTTP 429
        mockMvc.perform(get("/api/inventory")
                        .header("X-Forwarded-For", "127.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:problem:too-many-requests"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Refresh token rotation issues new access and refresh tokens, revoking the old one")
    void testRefreshTokenRotation() throws Exception {
        // 1. Login
        LoginRequest req = new LoginRequest("warehouse1", "Wh@12345");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResp = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class);
        String oldRefresh = loginResp.getRefreshToken();

        // 2. Refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(oldRefresh);
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        LoginResponse refreshResp = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), LoginResponse.class);
        String newRefresh = refreshResp.getRefreshToken();
        assertNotEquals(oldRefresh, newRefresh);

        // 3. Re-using old revoked refresh token fails
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Password policy enforcement rejects passwords lacking required complexity")
    void testPasswordPolicyValidation() throws Exception {
        // Login as warehouse1
        String token = authenticateAndGetToken("warehouse1", "Wh@12345");

        // Weak password (< 8 chars)
        ChangePasswordRequest weakReq = new ChangePasswordRequest("Wh@12345", "Short1!");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation-error"));

        // Valid strong password
        ChangePasswordRequest strongReq = new ChangePasswordRequest("Wh@12345", "NewSecurePassword@2026");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(strongReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @DisplayName("Vendor sees only its OWN catalog (vendor DB), not other vendors' products")
    void testVendorSeesOnlyOwnCatalog() throws Exception {
        String vendorToken = authenticateAndGetToken("vendor1", "Vendor@123");

        MvcResult result = mockMvc.perform(get("/api/vendor/catalog")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(1))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertTrue(json.contains("SKU-MILK-01"));      // vendor 1's own product
        assertFalse(json.contains("SKU-BREAD-03"));    // vendor 2's product - must not appear
        assertFalse(json.contains("SKU-SOAP-31"));     // vendor 3's product - must not appear
    }

    @Test
    @DisplayName("Vendor cannot read manager stock (/api/inventory is manager/admin only)")
    void testVendorCannotReadManagerInventory() throws Exception {
        String vendorToken = authenticateAndGetToken("vendor1", "Vendor@123");

        mockMvc.perform(get("/api/inventory")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Manager inventory lists all 50 SKUs and exposes no vendor names or vendor ids")
    void testManagerInventoryHasNoVendorData() throws Exception {
        String whToken = authenticateAndGetToken("warehouse1", "Wh@12345");

        MvcResult result = mockMvc.perform(get("/api/inventory")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andReturn();

        String json = result.getResponse().getContentAsString().toLowerCase();
        assertFalse(json.contains("vendor"));
        assertFalse(json.contains("freshfarms"));
    }

    @Test
    @DisplayName("Manager catalog shows a vendor every product and flags the ones already in its own catalog")
    void testManagerCatalogForVendor() throws Exception {
        String vendorToken = authenticateAndGetToken("vendor1", "Vendor@123");

        mockMvc.perform(get("/api/catalog")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[?(@.sku=='SKU-MILK-01')].inMyCatalog").value(true))
                .andExpect(jsonPath("$[?(@.sku=='SKU-BREAD-03')].inMyCatalog").value(false));
    }

    @Test
    @DisplayName("Warehouse Manager can replenish a SKU; a vendor cannot")
    void testManagerReplenish() throws Exception {
        String body = "{\"quantity\":25,\"reason\":\"top-up\"}";
        String whToken = authenticateAndGetToken("warehouse1", "Wh@12345");
        mockMvc.perform(post("/api/inventory/SKU-EGGS-02/replenish")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-EGGS-02"));

        String vendorToken = authenticateAndGetToken("vendor1", "Vendor@123");
        mockMvc.perform(post("/api/inventory/SKU-EGGS-02/replenish")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vendor orders a product OUTSIDE its catalog; manager dispatches; vendor receipt adds it to the vendor catalog")
    void testVendorOrdersFromManagerCatalogEndToEnd() throws Exception {
        String vendorToken = authenticateAndGetToken("vendor1", "Vendor@123");
        String whToken = authenticateAndGetToken("warehouse1", "Wh@12345");

        // SKU-BREAD-03 belongs to vendor 2's range, not vendor 1's
        MvcResult created = mockMvc.perform(post("/api/reorders")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-BREAD-03\",\"quantity\":10,\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(1))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("reorderId").asLong();

        // a vendor may not push the manager-side steps
        mockMvc.perform(post("/api/reorders/" + id + "/acknowledge")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reorders/" + id + "/acknowledge")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reorders/" + id + "/ship")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reorders/" + id + "/deliver")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/vendor/catalog")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.sku=='SKU-BREAD-03')].quantity").value(10))
                .andExpect(jsonPath("$.items[?(@.sku=='SKU-BREAD-03')].source").value("MANAGER_CATALOG"));

        // manager's view of reorders carries no vendor name
        MvcResult list = mockMvc.perform(get("/api/reorders")
                        .header("Authorization", "Bearer " + whToken))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(list.getResponse().getContentAsString().toLowerCase().contains("vendorname"));
    }

    @Test
    @DisplayName("Managers cannot place supply orders - only vendors order")
    void testManagerCannotCreateSupplyOrder() throws Exception {
        String whToken = authenticateAndGetToken("warehouse1", "Wh@12345");
        mockMvc.perform(post("/api/reorders")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-MILK-01\",\"quantity\":5,\"severity\":\"HIGH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role-based authorization: Warehouse Manager cannot adjust stock (ADMIN only)")
    void testRoleAccessControl() throws Exception {
        String whToken = authenticateAndGetToken("warehouse1", "Wh@12345");

        AdjustStockRequest adjustReq = new AdjustStockRequest(10, "Manual adjust");
        mockMvc.perform(post("/api/inventory/SKU-MILK-01/adjust")
                        .header("Authorization", "Bearer " + whToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:problem:access-denied"));

        // Admin succeeds
        String adminToken = authenticateAndGetToken("admin", "Admin@123");
        mockMvc.perform(post("/api/inventory/SKU-MILK-01/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adjustReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Engines health endpoint returns composite 4-engine snapshot")
    void testEnginesHealthEndpoint() throws Exception {
        String token = authenticateAndGetToken("admin", "Admin@123");

        mockMvc.perform(get("/api/engines/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.osEngine").exists())
                .andExpect(jsonPath("$.dbmsEngine").exists())
                .andExpect(jsonPath("$.dsaEngine").exists())
                .andExpect(jsonPath("$.webEngine").exists());
    }

    private String authenticateAndGetToken(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
        return resp.getAccessToken();
    }
}
