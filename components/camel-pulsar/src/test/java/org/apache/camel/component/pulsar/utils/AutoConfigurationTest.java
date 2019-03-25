package org.apache.camel.component.pulsar.utils;

import org.apache.camel.component.pulsar.configuration.AdminConfiguration;
import org.apache.pulsar.client.admin.Namespaces;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AutoConfigurationTest {

    @Mock
    private AdminConfiguration adminConfiguration;
    @Mock
    private PulsarAdmin pulsarAdmin;
    @Mock
    private Tenants tenants;
    @Mock
    private Namespaces namespaces;

    @Before
    public void setup() {
        when(adminConfiguration.getClusters()).thenReturn(Collections.singleton("standalone"));
    }

    @Test
    public void noAdminConfiguration() {
        when(pulsarAdmin.getClientConfigData()).thenReturn(null);

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void autoConfigurationDisabled() {
        when(pulsarAdmin.getClientConfigData()).thenReturn(adminConfiguration);
        when(adminConfiguration.isAutoCreateAllowed()).thenReturn(false);

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void defaultTopic() {
        when(pulsarAdmin.getClientConfigData()).thenReturn(adminConfiguration);
        when(adminConfiguration.isAutoCreateAllowed()).thenReturn(true);

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin);
        autoConfiguration.ensureNameSpaceAndTenant("topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void newTenantAndNamespace() throws PulsarAdminException {
        when(pulsarAdmin.getClientConfigData()).thenReturn(adminConfiguration);
        when(adminConfiguration.isAutoCreateAllowed()).thenReturn(true);
        when(pulsarAdmin.tenants()).thenReturn(tenants);
        when(tenants.getTenants()).thenReturn(Collections.<String>emptyList());
        when(pulsarAdmin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces("tn1")).thenReturn(Collections.<String>emptyList());

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(tenants).createTenant(eq("tn1"), Matchers.<TenantInfo>any());
        verify(namespaces).createNamespace("tn1/ns1", Collections.singleton("standalone"));
    }

    @Test
    public void existingTenantAndNamespace() throws PulsarAdminException {
        when(pulsarAdmin.getClientConfigData()).thenReturn(adminConfiguration);
        when(adminConfiguration.isAutoCreateAllowed()).thenReturn(true);
        when(pulsarAdmin.tenants()).thenReturn(tenants);
        when(tenants.getTenants()).thenReturn(Collections.<String>singletonList("tn1"));
        when(pulsarAdmin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces("tn1")).thenReturn(Collections.<String>singletonList("tn1/ns1"));

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(tenants, never()).createTenant(Matchers.<String>any(), Matchers.<TenantInfo>any());
        verify(namespaces, never()).createNamespace(Matchers.<String>any(), anySet());
    }
}
