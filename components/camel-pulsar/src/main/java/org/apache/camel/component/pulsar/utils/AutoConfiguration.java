/*
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

import java.util.List;
import java.util.Set;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automatically create Pulsar tenant and namespace if enabled
 */
public class AutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoConfiguration.class);

    private final PulsarAdmin pulsarAdmin;
    private final Set<String> clusters;

    public AutoConfiguration(PulsarAdmin pulsarAdmin, Set<String> clusters) {
        this.pulsarAdmin = pulsarAdmin;
        this.clusters = clusters;
    }

    public void ensureNameSpaceAndTenant(String path) {
        if (pulsarAdmin != null) {
            PulsarPath pulsarPath = new PulsarPath(path);
            if (pulsarPath.isAutoConfigurable()) {
                String tenant = pulsarPath.getTenant();
                String namespace = pulsarPath.getNamespace();
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
            pulsarAdmin.namespaces().createNamespace(namespace, clusters);
        }
    }

    private void ensureTenant(String tenant) throws PulsarAdminException {
        Tenants tenants = pulsarAdmin.tenants();
        List<String> tenantNames = tenants.getTenants();
        if (!tenantNames.contains(tenant)) {
            TenantInfo tenantInfo = new TenantInfo();
            tenantInfo.setAllowedClusters(clusters);
            pulsarAdmin.tenants().createTenant(tenant, tenantInfo);
        }
    }

    public boolean isAutoConfigurable() {
        return pulsarAdmin != null;
    }
}
