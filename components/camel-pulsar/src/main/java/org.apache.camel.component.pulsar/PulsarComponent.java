package org.apache.camel.component.pulsar;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.pulsar.configuration.AdminConfiguration;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultComponent;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.conf.ClientConfigurationData;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarComponent extends DefaultComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarComponent.class);

    private final PulsarEndpointConfiguration configuration;
    private final AdminConfiguration adminConfiguration;

    PulsarComponent(CamelContext context, PulsarEndpointConfiguration configuration, AdminConfiguration adminConfiguration) {
        super(context);
        this.configuration = configuration;
        this.adminConfiguration = adminConfiguration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String path, Map<String, Object> parameters) throws Exception {

        setProperties(configuration, parameters);

        ensureNameSpaceAndTenant(path);

        return PulsarEndpoint.create(uri, path, configuration, this);
    }

    private void ensureNameSpaceAndTenant(String path) {
        if(adminConfiguration != null && adminConfiguration.isAutoCreateAllowed()) {
            int index = path.indexOf("/");
            if (index > -1) {
                String tenant = path.substring(0, index);
                index++;
                String namespace = path.substring(0, path.indexOf("/", index));
                ClientConfigurationData clientConfigData = new ClientConfigurationData();
                try {
                    PulsarAdmin admin = new PulsarAdmin(adminConfiguration.getServiceUrl(), adminConfiguration);
                    List<String> tenants = admin.tenants().getTenants();
                    if (!tenants.contains(tenant)) {
                        TenantInfo tenantInfo = new TenantInfo();
                        tenantInfo.setAllowedClusters(adminConfiguration.getClusters());
                        admin.tenants().createTenant(tenant, tenantInfo);
                    }
                    List<String> namespaces = admin.namespaces().getNamespaces(tenant);
                    if (!namespaces.contains(namespace)) {
                        admin.namespaces().createNamespace(namespace, adminConfiguration.getClusters());
                    }
                } catch (PulsarClientException | PulsarAdminException e) {
                    LOGGER.error(e.getMessage());
                }
            }
        }
    }
}
