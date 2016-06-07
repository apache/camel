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
package org.apache.camel.component.etcd.processor.remote;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.util.ObjectHelper;

public final class EtcdServiceCallServerListStrategies {
    private EtcdServiceCallServerListStrategies() {
    }

    public static final class OnDemand extends EtcdServiceCallServerListStrategy {
        public OnDemand(EtcdConfiguration configuration) throws Exception {
            super(configuration);
        }

        @Override
        public List<ServiceCallServer> getUpdatedListOfServers(String name) {
            List<ServiceCallServer> servers = Collections.emptyList();
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
                        .filter(s -> name.equalsIgnoreCase(s.getName()))
                        .sorted(EtcdServiceCallServer.COMPARATOR)
                        .collect(Collectors.toList());
                }
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }

            return servers;
        }

        @Override
        public String toString() {
            return "OnDemand";
        }
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static EtcdServiceCallServerListStrategy onDemand(EtcdConfiguration configuration) throws Exception {
        return new OnDemand(configuration);
    }
}
