package com.galeno.shared.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Consultorio (tenant) del hilo actual. Lo fija {@link TenantFilter} en cada petición
 * a partir del JWT, o el worker de IA al procesar un trabajo.
 */
public final class TenantContext {

    /** Tenant "raíz": Hibernate no aplica el filtro de tenant. Solo para procesos del sistema. */
    public static final UUID SYSTEM = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
