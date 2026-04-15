package com.unios.security.multi_tenancy;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadLocal container for the current tenant ID.
 * Ensures data isolation within a single request thread.
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(Long tenantId) {
        log.debug("Setting current tenant to: {}", tenantId);
        currentTenant.set(tenantId);
    }

    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenant.remove();
    }
}
