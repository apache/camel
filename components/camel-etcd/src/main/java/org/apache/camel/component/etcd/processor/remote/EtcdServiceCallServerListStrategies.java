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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.requests.EtcdKeyGetRequest;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EtcdServiceCallServerListStrategies {

    private abstract static class AbstractStrategy extends EtcdServiceCallServerListStrategy {
        AbstractStrategy(EtcdConfiguration configuration) throws Exception {
            super(configuration);
        }

        protected List<EtcdServiceCallServer> getServers() {
            return getServers(s -> true);
        }

        protected List<EtcdServiceCallServer> getServers(Predicate<EtcdServiceCallServer> filter) {
            List<EtcdServiceCallServer> servers = Collections.emptyList();

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
                            .sorted(EtcdServiceCallServer.COMPARATOR)
                            .collect(Collectors.toList());
                    }
                } catch (Exception e) {
                    throw new RuntimeCamelException(e);
                }
            }

            return servers;
        }
    }

    private EtcdServiceCallServerListStrategies() {
    }

    public static final class OnDemand extends AbstractStrategy {
        public OnDemand(EtcdConfiguration configuration) throws Exception {
            super(configuration);
        }

        @Override
        public List<EtcdServiceCallServer> getUpdatedListOfServers(String name) {
            return getServers(s -> name.equalsIgnoreCase(s.getName()));
        }

        @Override
        public String toString() {
            return "EtcdServiceCallServerListStrategy.OnDemand";
        }
    }

    public static final class Watch extends AbstractStrategy
            implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> {

        private static final Logger LOGGER = LoggerFactory.getLogger(Watch.class);
        private final AtomicReference<List<EtcdServiceCallServer>> serversRef;
        private final AtomicLong index;
        private final String servicePath;

        public Watch(EtcdConfiguration configuration) throws Exception {
            super(configuration);

            this.serversRef = new AtomicReference<>();
            this.index = new AtomicLong(0);
            this.servicePath = ObjectHelper.notNull(configuration.getServicePath(), "servicePath");
        }

        @Override
        public List<EtcdServiceCallServer> getUpdatedListOfServers(String name) {
            List<EtcdServiceCallServer> servers = serversRef.get();
            if (servers == null) {
                serversRef.set(getServers());
                watch();
            }

            return serversRef.get().stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "EtcdServiceCallServerListStrategy.Watch";
        }

        // *************************************************************************
        // Watch
        // *************************************************************************

        @Override
        public void onResponse(ResponsePromise<EtcdKeysResponse> promise) {
            if (!isRunAllowed()) {
                return;
            }

            Throwable throwable = promise.getException();
            if (throwable != null && throwable instanceof EtcdException) {
                EtcdException exception = (EtcdException) throwable;
                if (EtcdHelper.isOutdatedIndexException(exception)) {
                    LOGGER.debug("Outdated index, key={}, cause={}", servicePath, exception.etcdCause);
                    index.set(exception.index + 1);
                }
            } else {
                try {
                    EtcdKeysResponse response = promise.get();
                    EtcdHelper.setIndex(index, response);

                    serversRef.set(getServers());
                } catch (TimeoutException e) {
                    LOGGER.debug("Timeout watching for {}", getConfiguration().getServicePath());
                    throwable = null;
                } catch (Exception e) {
                    throwable = e;
                }
            }

            if (throwable == null) {
                watch();
            } else {
                throw new RuntimeCamelException(throwable);
            }
        }

        private void watch() {
            if (!isRunAllowed()) {
                return;
            }

            try {
                getClient().get(servicePath)
                    .recursive()
                    .waitForChange(index.get())
                    .timeout(1, TimeUnit.SECONDS)
                    .send()
                    .addListener(this);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    // *************************************************************************
    // Helpers
    // *************************************************************************

    public static EtcdServiceCallServerListStrategy onDemand(EtcdConfiguration configuration) throws Exception {
        return new OnDemand(configuration);
    }

    public static EtcdServiceCallServerListStrategy watch(EtcdConfiguration configuration) throws Exception {
        return new Watch(configuration);
    }
}
