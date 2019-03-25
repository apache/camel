package org.apache.camel.component.pulsar.utils;

import org.apache.camel.component.pulsar.configuration.AdminConfiguration;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoConfiguration.class);
    private static final Pattern pattern = Pattern.compile("^(?<namespace>(?<tenant>.+)/.+)/.+$");


    private PulsarAdmin pulsarAdmin;
    private AdminConfiguration adminConfiguration;

    public AutoConfiguration(PulsarAdmin pulsarAdmin) {
        setPulsarAdmin(pulsarAdmin);
    }

    public AutoConfiguration(){}

    public void ensureNameSpaceAndTenant(String path) {
        if(pulsarAdmin != null && adminConfiguration != null && adminConfiguration.isAutoCreateAllowed()) {
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                String tenant = matcher.group("tenant");
                String namespace = matcher.group("namespace");
                try {
                    ensureTenant(tenant);
                    ensureNameSpace(tenant, namespace);
                } catch (PulsarAdminException e) {
                    LOGGER.error(e.getMessage());
                }
            }
        }
    }

    private void ensureNameSpace(String tenant, String namespace) throws PulsarAdminException {
        List<String> namespaces = pulsarAdmin.namespaces().getNamespaces(tenant);
        if (!namespaces.contains(namespace)) {
            pulsarAdmin.namespaces().createNamespace(namespace, adminConfiguration.getClusters());
        }
    }

    private void ensureTenant(String tenant) throws PulsarAdminException {
        Tenants tenants1 = pulsarAdmin.tenants();
        List<String> tenants = tenants1.getTenants();
        if (!tenants.contains(tenant)) {
            TenantInfo tenantInfo = new TenantInfo();
            tenantInfo.setAllowedClusters(adminConfiguration.getClusters());
            pulsarAdmin.tenants().createTenant(tenant, tenantInfo);
        }
    }

    public PulsarAdmin getPulsarAdmin() {
        return pulsarAdmin;
    }

    public void setPulsarAdmin(PulsarAdmin pulsarAdmin) {
        this.pulsarAdmin = pulsarAdmin;
        adminConfiguration = (AdminConfiguration) pulsarAdmin.getClientConfigData();
    }
}
