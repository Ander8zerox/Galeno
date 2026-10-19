package com.galeno.shared.tenancy;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Multi-tenant por columna: toda entidad con {@code @TenantId} se filtra y se rellena
 * automáticamente con el consultorio del contexto. Sin tenant, las consultas no devuelven nada.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    private static final UUID NONE = new UUID(0L, 1L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenant = TenantContext.get();
        return tenant != null ? tenant : NONE;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return TenantContext.SYSTEM.equals(tenantId);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
