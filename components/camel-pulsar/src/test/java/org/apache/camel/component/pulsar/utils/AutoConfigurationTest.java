/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.pulsar.utils;

import org.apache.pulsar.client.admin.Namespaces;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import java.util.Collections;
import java.util.Set;

import static org.mockito.Mockito.*;

public class AutoConfigurationTest {

    private PulsarAdmin pulsarAdmin;
    private Tenants tenants;
    private Namespaces namespaces;
    private Set<String> clusters = Collections.singleton("standalone");

    @Before
    public void setUp() {
        pulsarAdmin = mock(PulsarAdmin.class);
        tenants = mock(Tenants.class);
        namespaces = mock(Namespaces.class);

        when(pulsarAdmin.tenants()).thenReturn(tenants);
        when(pulsarAdmin.namespaces()).thenReturn(namespaces);
    }

    @Test
    public void noAdminConfiguration() {
        when(pulsarAdmin.getClientConfigData()).thenReturn(null);

        AutoConfiguration autoConfiguration = new AutoConfiguration(null, clusters);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void autoConfigurationDisabled() {

        AutoConfiguration autoConfiguration = new AutoConfiguration(null, clusters);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void defaultTopic() {

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin, clusters);
        autoConfiguration.ensureNameSpaceAndTenant("topic");

        verify(pulsarAdmin, never()).tenants();
    }

    @Test
    public void newTenantAndNamespace() throws PulsarAdminException {
        when(pulsarAdmin.tenants()).thenReturn(tenants);
        when(tenants.getTenants()).thenReturn(Collections.<String>emptyList());
        when(pulsarAdmin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces("tn1")).thenReturn(Collections.<String>emptyList());

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin, clusters);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(tenants).createTenant(eq("tn1"), Matchers.<TenantInfo>any());
        verify(namespaces).createNamespace("tn1/ns1", Collections.singleton("standalone"));
    }

    @Test
    public void existingTenantAndNamespace() throws PulsarAdminException {
        when(pulsarAdmin.tenants()).thenReturn(tenants);
        when(tenants.getTenants()).thenReturn(Collections.<String>singletonList("tn1"));
        when(pulsarAdmin.namespaces()).thenReturn(namespaces);
        when(namespaces.getNamespaces("tn1")).thenReturn(Collections.<String>singletonList("tn1/ns1"));

        AutoConfiguration autoConfiguration = new AutoConfiguration(pulsarAdmin, clusters);
        autoConfiguration.ensureNameSpaceAndTenant("tn1/ns1/topic");

        verify(tenants, never()).createTenant(Matchers.<String>any(), Matchers.<TenantInfo>any());
        verify(namespaces, never()).createNamespace(Matchers.<String>any(), anySet());
    }
}
