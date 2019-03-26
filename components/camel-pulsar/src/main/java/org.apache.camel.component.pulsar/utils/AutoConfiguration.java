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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What is the purpose of this? Needs documentation here
 */
@UriParams
public class AutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoConfiguration.class);
    private static final Pattern URI_PATTERN = Pattern.compile("^(?<namespace>(?<tenant>.+)/.+)/.+$");

    @UriParam(label = "admin", description = "The Pulsar Admin client Bean")
    private PulsarAdmin pulsarAdmin;

    public AutoConfiguration(PulsarAdmin pulsarAdmin) {
        setPulsarAdmin(pulsarAdmin);
    }

    public AutoConfiguration() {
    }

    public void ensureNameSpaceAndTenant(String path) {
        if(pulsarAdmin != null) {
            Matcher matcher = URI_PATTERN.matcher(path);
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
            // TODO find a way to pass the cluster names into this method
            Set<String> clusters = Collections.singleton("standalone");
            pulsarAdmin.namespaces().createNamespace(namespace, clusters);
        }
    }

    private void ensureTenant(String tenant) throws PulsarAdminException {
        Tenants tenants1 = pulsarAdmin.tenants();
        List<String> tenants = tenants1.getTenants();
        if (!tenants.contains(tenant)) {
            TenantInfo tenantInfo = new TenantInfo();
            // TODO find a way to pass the cluster names into this method
            Set<String> clusters = Collections.singleton("standalone");
            tenantInfo.setAllowedClusters(clusters);
            pulsarAdmin.tenants().createTenant(tenant, tenantInfo);
        }
    }

    public PulsarAdmin getPulsarAdmin() {
        return pulsarAdmin;
    }

    private void setPulsarAdmin(PulsarAdmin pulsarAdmin) {
        this.pulsarAdmin = pulsarAdmin;
    }
}
