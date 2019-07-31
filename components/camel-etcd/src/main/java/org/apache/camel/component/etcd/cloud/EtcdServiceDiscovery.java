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
package org.apache.camel.component.etcd.cloud;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class EtcdServiceDiscovery extends DefaultServiceDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdServiceDiscovery.class);
    private static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();

    private final EtcdConfiguration configuration;
    private EtcdClient client;

    EtcdServiceDiscovery(EtcdConfiguration configuration) {
        this.configuration = configuration;
        this.client = null;
    }

    @Override
    protected void doStart() throws Exception {
        if (client == null) {
            client = configuration.createClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    protected EtcdConfiguration getConfiguration() {
        return this.configuration;
    }

    protected EtcdClient getClient() {
        return this.client;
    }

    protected EtcdServiceDefinition nodeFromString(String value) {
        EtcdServiceDefinition server = null;

        try {
            server = MAPPER.readValue(value, EtcdServiceDefinition.class);
        } catch (Exception e) {
            LOGGER.warn("", e);
        }

        return server;
    }

    protected List<ServiceDefinition> getServices() {
        return getServices(s -> true);
    }

    protected List<ServiceDefinition> getServices(Predicate<EtcdServiceDefinition> filter) {
        List<ServiceDefinition> servers = Collections.emptyList();

        if (isRunAllowed()) {
            try {
                final EtcdConfiguration conf = getConfiguration();
                final EtcdKeyGetRequest request = getClient().get(conf.getServicePath()).recursive();
                if (conf.hasTimeout()) {
                    request.timeout(conf.getTimeout(), TimeUnit.SECONDS);
                }

                final EtcdKeysResponse response = request.send().get();

                if (Objects.nonNull(response.node) && !response.node.nodes.isEmpty()) {
                    servers = response.node.nodes.stream()
                        .map(node -> node.value)
                        .filter(ObjectHelper::isNotEmpty)
                        .map(this::nodeFromString)
                        .filter(Objects::nonNull)
                        .filter(filter)
                        .sorted(EtcdServiceDefinition.COMPARATOR)
                        .collect(Collectors.toList());
                }
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }

        return servers;
    }
}
