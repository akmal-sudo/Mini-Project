package com.project.web.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.security.Principal;

/**
 * Custom authenticated principal carrying user identifier, role, and associated vendorId.
 */
@Getter
@AllArgsConstructor
public class UserPrincipal implements Principal {
    private final Integer userId;
    private final String username;
    private final String role;
    private final Integer vendorId;

    @Override
    public String getName() {
        return username;
    }
}
