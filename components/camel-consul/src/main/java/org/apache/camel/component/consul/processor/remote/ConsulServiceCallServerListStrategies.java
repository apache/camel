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
import java.util.stream.Collectors;

import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.spi.ServiceCallServer;

public final class ConsulServiceCallServerListStrategies {
    private ConsulServiceCallServerListStrategies() {
    }

    public static final class OnDemand extends ConsulServiceCallServerListStrategy {
        public OnDemand(ConsulConfiguration configuration) throws Exception {
            super(configuration);
        }

        @Override
        public List<ServiceCallServer> getUpdatedListOfServers(String name) {
            List<CatalogService> services = getCatalogClient()
                .getService(name, getCatalogOptions())
                .getResponse();

            List<ServiceHealth> healths = getHealthClient()
                .getAllServiceInstances(name, getCatalogOptions())
                .getResponse();

            return services.stream()
                .filter(service -> !hasFailingChecks(service, healths))
                .map(this::newServer)
                .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "OnDemand";
        }
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static ConsulServiceCallServerListStrategy onDemand(ConsulConfiguration configuration) throws Exception {
        return new OnDemand(configuration);
    }
}
