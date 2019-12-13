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
package org.apache.camel.component.consul.cloud;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.orbitz.consul.Consul;
import com.orbitz.consul.NotRegisteredException;
import com.orbitz.consul.model.agent.ImmutableRegCheck;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.Service;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.impl.cloud.AbstractServiceRegistry;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsulServiceRegistry extends AbstractServiceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulServiceRegistry.class);

    private final Set<String> serviceList;
    private ConsulServiceRegistryConfiguration configuration;
    private Consul client;
    private ScheduledExecutorService scheduler;

    public ConsulServiceRegistry() {
        this.serviceList = ConcurrentHashMap.newKeySet();
        this.configuration = new ConsulServiceRegistryConfiguration();
    }

    public ConsulServiceRegistry(ConsulServiceRegistryConfiguration configuration) {
        this.serviceList = ConcurrentHashMap.newKeySet();
        this.configuration = configuration.copy();
    }

    // ****************
    // Properties
    // ****************

    public ConsulServiceRegistryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConsulServiceRegistryConfiguration configuration) {
        this.configuration = configuration.copy();
    }

    public String getUrl() {
        return configuration.getUrl();
    }

    public void setUrl(String url) {
        configuration.setUrl(url);
    }

    public String getDatacenter() {
        return configuration.getDatacenter();
    }

    public void setDatacenter(String datacenter) {
        configuration.setDatacenter(datacenter);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }

    public String getAclToken() {
        return configuration.getAclToken();
    }

    public void setAclToken(String aclToken) {
        configuration.setAclToken(aclToken);
    }

    public String getUserName() {
        return configuration.getUserName();
    }

    public void setUserName(String userName) {
        configuration.setUserName(userName);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public Long getConnectTimeoutMillis() {
        return configuration.getConnectTimeoutMillis();
    }

    public void setConnectTimeoutMillis(Long connectTimeoutMillis) {
        configuration.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    public Long getReadTimeoutMillis() {
        return configuration.getReadTimeoutMillis();
    }

    public void setReadTimeoutMillis(Long readTimeoutMillis) {
        configuration.setReadTimeoutMillis(readTimeoutMillis);
    }

    public Long getWriteTimeoutMillis() {
        return configuration.getWriteTimeoutMillis();
    }

    public void setWriteTimeoutMillis(Long writeTimeoutMillis) {
        configuration.setWriteTimeoutMillis(writeTimeoutMillis);
    }

    public Integer getBlockSeconds() {
        return configuration.getBlockSeconds();
    }

    public void setBlockSeconds(Integer blockSeconds) {
        configuration.setBlockSeconds(blockSeconds);
    }

    public boolean isOverrideServiceHost() {
        return configuration.isOverrideServiceHost();
    }

    public void setOverrideServiceHost(boolean overrideServiceHost) {
        configuration.setOverrideServiceHost(overrideServiceHost);
    }

    public String getServiceHost() {
        return configuration.getServiceHost();
    }

    public void setServiceHost(String serviceHost) {
        configuration.setServiceHost(serviceHost);
    }

    public int getCheckTtl() {
        return configuration.getCheckTtl();
    }

    public void setCheckTtl(int checkTtl) {
        configuration.setCheckTtl(checkTtl);
    }

    public int getCheckInterval() {
        return configuration.getCheckInterval();
    }

    public void setCheckInterval(int checkInterval) {
        configuration.setCheckInterval(checkInterval);
    }

    public int getDeregisterAfter() {
        return configuration.getDeregisterAfter();
    }

    public void setDeregisterAfter(int deregisterAfter) {
        configuration.setDeregisterAfter(deregisterAfter);
    }

    public boolean isDeregisterServicesOnStop() {
        return configuration.isDeregisterServicesOnStop();
    }

    public void setDeregisterServicesOnStop(boolean deregisterServicesOnStop) {
        configuration.setDeregisterServicesOnStop(deregisterServicesOnStop);
    }

    // ****************
    // Lifecycle
    // ****************

    @Override
    protected void doStart() throws Exception {
        client = this.configuration.createConsulClient(getCamelContext());
        scheduler = getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "ConsulServiceRegistry");
    }

    @Override
    protected void doStop() throws Exception {
        if (scheduler != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(scheduler);
            scheduler = null;
        }

        if (configuration.isDeregisterServicesOnStop()) {
            for (Service service : client.agentClient().getServices().values()) {
                try {
                    if (serviceList.contains(service.getId())) {
                        client.agentClient().deregister(service.getId());
                        serviceList.remove(service.getId());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error de-registering service: " + service, e);
                }
            }

            client = null;
        }
    }

    // ****************
    // Registry
    // ****************

    @Override
    public void register(ServiceDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("Service ID must be defined (definition=" + definition + ")");
        }
        if (definition.getName() == null) {
            throw new IllegalArgumentException("Service Name must be defined (definition=" + definition + ")");
        }

        Registration registration = ImmutableRegistration.builder().address(computeServiceHost(definition)).port(definition.getPort()).name(definition.getName())
            .id(definition.getId()).check(computeCheck(definition))
            .tags(definition.getMetadata().entrySet().stream().filter(e -> e.getValue() != null).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList()))
            .addTags("_consul.service.registry.id=" + getId()).build();

        // perform service registration against consul
        client.agentClient().register(registration);

        try {
            // mark the service as healthy
            client.agentClient().pass(definition.getId());

            // If the service has TTL enabled
            registration.getCheck().flatMap(Registration.RegCheck::getTtl).ifPresent(ignored -> {
                LOGGER.debug("Configure service pass for: {}", definition);

                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (serviceList.contains(definition.getId())) {
                            client.agentClient().pass(definition.getId());
                        }
                    } catch (NotRegisteredException e) {
                        LOGGER.warn("Service with id: {} is not more registered", definition.getId());
                        serviceList.remove(definition.getId());
                    }
                }, configuration.getCheckInterval() / 2, configuration.getCheckInterval(), TimeUnit.SECONDS);
            });
        } catch (NotRegisteredException e) {
            LOGGER.warn("There was an issue registering service: {}", definition.getId());
        }

        // add the serviceId to the list of known server
        serviceList.add(definition.getId());
    }

    @Override
    public void deregister(ServiceDefinition definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("ServiceID must be defined (definition=" + definition + ")");
        }

        client.agentClient().deregister(definition.getId());

        // remove the serviceId to the list of known server
        serviceList.remove(definition.getId());
    }

    private String computeServiceHost(ServiceDefinition definition) {
        String host = definition.getHost();

        if (configuration.isOverrideServiceHost() && configuration.getServiceHost() != null) {
            host = configuration.getServiceHost();
        }

        return ObjectHelper.notNull(host, "service host");
    }

    // TODO: this need to be improved
    private Registration.RegCheck computeCheck(ServiceDefinition definition) {
        if (definition.getHealth() == null) {
            return ImmutableRegCheck.builder().ttl(String.format("%ss", configuration.getCheckInterval()))
                .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build();
        }

        return definition.getHealth().getEndpoint().flatMap(uri -> {
            if (Objects.equals("http", uri.getScheme())) {
                return Optional.of(ImmutableRegCheck.builder().http(uri.toASCIIString()).interval(String.format("%ss", configuration.getCheckInterval()))
                    .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build());
            }
            if (Objects.equals("https", uri.getScheme())) {
                return Optional.of(ImmutableRegCheck.builder().http(uri.toASCIIString()).interval(String.format("%ss", configuration.getCheckInterval()))
                    .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build());
            }
            if (Objects.equals("tcp", uri.getScheme())) {
                return Optional.of(ImmutableRegCheck.builder().tcp(uri.getHost()).interval(String.format("%ss", configuration.getCheckInterval()))
                    .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build());
            }
            if (Objects.equals("grpc", uri.getScheme())) {
                return Optional.of(ImmutableRegCheck.builder().grpc(uri.getHost()).interval(String.format("%ss", configuration.getCheckInterval()))
                    .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build());
            }

            return Optional.empty();
        }).orElseGet(() -> ImmutableRegCheck.builder().ttl(String.format("%ss", configuration.getCheckInterval()))
            .deregisterCriticalServiceAfter(String.format("%ss", configuration.getDeregisterAfter())).build());
    }
}
