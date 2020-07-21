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
package org.apache.camel.component.zookeeper.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonRootName;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorHelper;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperServiceDiscovery extends DefaultServiceDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperServiceDiscovery.class);

    private final ZooKeeperCuratorConfiguration configuration;
    private final boolean managedInstance;
    private CuratorFramework curator;
    private ServiceDiscovery<MetaData> serviceDiscovery;

    public ZooKeeperServiceDiscovery(ZooKeeperCuratorConfiguration configuration) {
        this.configuration = configuration;
        this.curator = configuration.getCuratorFramework();
        this.managedInstance = Objects.isNull(curator);
    }

    // *********************************************
    // Lifecycle
    // *********************************************

    @Override
    protected void doStart() throws Exception {
        if (curator == null) {
            // Validation
            ObjectHelper.notNull(getCamelContext(), "Camel Context");
            ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

            LOGGER.debug("Starting ZooKeeper Curator with namespace '{}',  nodes: '{}'",
                configuration.getNamespace(),
                String.join(",", configuration.getNodes())
            );

            curator = ZooKeeperCuratorHelper.createCurator(configuration);
            curator.start();
        }

        if (serviceDiscovery == null) {
            // Validation
            ObjectHelper.notNull(configuration.getBasePath(), "ZooKeeper base path");

            LOGGER.debug("Starting ZooKeeper ServiceDiscoveryBuilder with base path '{}'",
                configuration.getBasePath()
            );

            serviceDiscovery = ZooKeeperCuratorHelper.createServiceDiscovery(configuration, curator, MetaData.class);
            serviceDiscovery.start();
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (serviceDiscovery != null) {
            try {
                serviceDiscovery.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing Curator ServiceDiscovery", e);
            }
        }

        if (curator != null && managedInstance) {
            curator.close();
        }
    }

    // *********************************************
    // Implementation
    // *********************************************

    @Override
    public List<ServiceDefinition> getServices(String name) {
        if (serviceDiscovery == null) {
            return Collections.emptyList();
        }

        try {
            return serviceDiscovery.queryForInstances(name).stream()
                .map(si -> {
                    Map<String, String> meta = new HashMap<>();
                    ObjectHelper.ifNotEmpty(si.getPayload(), meta::putAll);

                    meta.putIfAbsent(ServiceDefinition.SERVICE_META_NAME, si.getName());
                    meta.putIfAbsent(ServiceDefinition.SERVICE_META_ID, si.getId());

                    return new DefaultServiceDefinition(
                        si.getName(),
                        si.getAddress(),
                        si.getSslPort() != null ? si.getSslPort() : si.getPort(),
                        meta);
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    // *********************************************
    // Helpers
    // *********************************************

    @JsonRootName("meta")
    public static final class MetaData extends HashMap<String, String> {
    }
}
