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

package org.apache.camel.component.ribbon.cloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.PollingServerListUpdater;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryAware;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterAware;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.cloud.ServiceLoadBalancerFunction;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RibbonServiceLoadBalancer
        extends ServiceSupport
        implements CamelContextAware, ServiceDiscoveryAware, ServiceFilterAware, ServiceLoadBalancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RibbonServiceLoadBalancer.class);

    private final RibbonConfiguration configuration;
    private final ConcurrentMap<String, ZoneAwareLoadBalancer<RibbonServiceDefinition>> loadBalancers;
    private CamelContext camelContext;
    private ServiceDiscovery serviceDiscovery;
    private ServiceFilter serviceFilter;

    public RibbonServiceLoadBalancer(RibbonConfiguration configuration) {
        this.configuration = configuration;
        this.loadBalancers = new ConcurrentHashMap<>();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    @Override
    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public ServiceFilter getServiceFilter() {
        return serviceFilter;
    }

    @Override
    public void setServiceFilter(ServiceFilter serviceFilter) {
        this.serviceFilter = serviceFilter;
    }

    // ************************
    // lifecycle
    // ************************

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(camelContext, "camel context");

        if (serviceDiscovery != null) {
            LOGGER.info("ServiceCall is using ribbon load balancer with service discovery type: {} and service filter: {}",
                serviceDiscovery.getClass(),
                serviceDiscovery != null ? serviceFilter.getClass() : "none");
        } else {
            LOGGER.info("ServiceCall is using ribbon load balancer");
        }

        ServiceHelper.startService(serviceDiscovery);
    }

    @Override
    protected void doStop() throws Exception {
        loadBalancers.values().forEach(ZoneAwareLoadBalancer::stopServerListRefreshing);
        loadBalancers.clear();

        ServiceHelper.stopService(serviceDiscovery);
    }

    // ************************
    // Processor
    // ************************

    @Override
    public <T> T process(String serviceName, ServiceLoadBalancerFunction<T> request) throws Exception {
        ILoadBalancer loadBalancer = loadBalancers.computeIfAbsent(serviceName, key -> createLoadBalancer(key));
        Server server = loadBalancer.chooseServer(serviceName);

        if (server == null) {
            throw new RejectedExecutionException("No active services with name " + serviceName);
        }

        ServiceDefinition definition;

        if (server instanceof ServiceDefinition) {
            // If the service discovery is one of camel provides, the definition
            // is already of the expected type.
            definition = (ServiceDefinition)server;
        } else {
            // If ribbon server list is configured through client config properties
            // i.e. with listOfServers property the instance provided by the load
            // balancer is of type Server so a conversion is needed
            definition = new RibbonServiceDefinition(
                serviceName,
                server.getHost(),
                server.getPort()
            );

            String zone = server.getZone();
            if (zone != null) {
                server.setZone(zone);
            }
        }

        return request.apply(definition);
    }

    // ************************
    // Helpers
    // ************************

    private ZoneAwareLoadBalancer<RibbonServiceDefinition> createLoadBalancer(String serviceName) {
        // setup client config
        IClientConfig config = configuration.getClientName() != null
            ? IClientConfig.Builder.newBuilder(configuration.getClientName()).build()
            : IClientConfig.Builder.newBuilder().build();

        if (configuration.getProperties() != null) {
            for (Map.Entry<String, String> entry : configuration.getProperties().entrySet()) {
                IClientConfigKey key = IClientConfigKey.Keys.valueOf(entry.getKey());
                String value = entry.getValue();

                LOGGER.debug("RibbonClientConfig: {}={}", key.key(), value);
                config.set(key, value);
            }
        }

        ZoneAwareLoadBalancer<RibbonServiceDefinition> loadBalancer;

        if (serviceDiscovery != null) {
            loadBalancer = new ZoneAwareLoadBalancer<>(
                config,
                configuration.getRuleOrDefault(RoundRobinRule::new),
                configuration.getPingOrDefault(DummyPing::new),
                new RibbonServerList(serviceName, serviceDiscovery, serviceFilter),
                null,
                new PollingServerListUpdater(config));
        } else {
            loadBalancer = new ZoneAwareLoadBalancer<>(config);
        }

        return loadBalancer;
    }

    static final class RibbonServerList implements ServerList<RibbonServiceDefinition>  {
        private final String serviceName;
        private final ServiceDiscovery serviceDiscovery;
        private final ServiceFilter serviceFilter;

        RibbonServerList(String serviceName, ServiceDiscovery serviceDiscovery, ServiceFilter serviceFilter) {
            this.serviceName = serviceName;
            this.serviceDiscovery = serviceDiscovery;
            this.serviceFilter = serviceFilter;
        }

        @Override
        public List<RibbonServiceDefinition> getInitialListOfServers() {
            List<ServiceDefinition> services = serviceDiscovery.getServices(serviceName);
            if (serviceFilter != null) {
                services = serviceFilter.apply(services);
            }

            return asRibbonServerList(services);
        }

        @Override
        public List<RibbonServiceDefinition> getUpdatedListOfServers() {
            List<ServiceDefinition> services = serviceDiscovery.getServices(serviceName);
            if (serviceFilter != null) {
                services = serviceFilter.apply(services);
            }

            return asRibbonServerList(services);
        }

        private List<RibbonServiceDefinition> asRibbonServerList(List<ServiceDefinition> services) {
            List<RibbonServiceDefinition> ribbonServers = new ArrayList<>();

            for (ServiceDefinition service : services) {
                if (service instanceof RibbonServiceDefinition) {
                    ribbonServers.add((RibbonServiceDefinition)service);
                } else {
                    RibbonServiceDefinition serviceDef = new RibbonServiceDefinition(
                        serviceName,
                        service.getHost(),
                        service.getPort(),
                        service.getMetadata(),
                        service.getHealth()
                    );

                    String zone = serviceDef.getMetadata().get("zone");
                    if (zone != null) {
                        serviceDef.setZone(zone);
                    }

                    ribbonServers.add(serviceDef);
                }
            }

            return ribbonServers;
        }
    }
}
