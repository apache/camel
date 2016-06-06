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
package org.apache.camel.component.consul.processor.remote;

import java.util.List;

import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.CatalogOptions;
import com.orbitz.consul.option.ImmutableCatalogOptions;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.impl.remote.DefaultServiceCallServer;
import org.apache.camel.impl.remote.DefaultServiceCallServerListStrategy;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.util.ObjectHelper;


abstract class ConsulServiceCallServerListStrategy extends DefaultServiceCallServerListStrategy<ServiceCallServer> {
    private final Consul client;
    private final CatalogOptions catalogOptions;

    ConsulServiceCallServerListStrategy(ConsulConfiguration configuration) throws Exception {
        this.client = configuration.createConsulClient();

        ImmutableCatalogOptions.Builder builder = ImmutableCatalogOptions.builder();
        if (ObjectHelper.isNotEmpty(configuration.getDc())) {
            builder.datacenter(configuration.getDc());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTags())) {
            configuration.getTags().forEach(builder::tag);
        }

        catalogOptions = builder.build();
    }

    @Override
    public String toString() {
        return "ConsulServiceCallServerListStrategy";
    }

    // *************************
    // Getter
    // *************************

    protected Consul getClient() {
        return client;
    }

    protected CatalogClient getCatalogClient() {
        return client.catalogClient();
    }

    protected HealthClient getHealthClient() {
        return client.healthClient();
    }

    protected CatalogOptions getCatalogOptions() {
        return catalogOptions;
    }

    // *************************
    // Helpers
    // *************************

    protected boolean isNotHealthy(HealthCheck check) {
        final String status = check.getStatus();
        return status != null && !status.equalsIgnoreCase("passing");
    }

    protected boolean isNotHealthy(ServiceHealth health) {
        return health.getChecks().stream().anyMatch(this::isNotHealthy);
    }

    protected boolean isCheckOnService(ServiceHealth check, CatalogService service) {
        return check.getService().getService().equalsIgnoreCase(service.getServiceName());
    }

    protected boolean hasFailingChecks(CatalogService service, List<ServiceHealth> healths) {
        return healths.stream().anyMatch(health -> isCheckOnService(health, service) && isNotHealthy(health));
    }

    protected ServiceCallServer newServer(CatalogService service) {
        return new DefaultServiceCallServer(
            service.getServiceAddress(),
            service.getServicePort()
        );
    }
}
