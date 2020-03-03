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
package org.apache.camel.component.etcd.support;

import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.component.etcd.EtcdKeysComponent;
import org.apache.camel.component.etcd.EtcdStatsComponent;
import org.apache.camel.component.etcd.EtcdWatchComponent;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.junit5.Wait;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

public abstract class EtcdTestSupport extends ContainerAwareTestSupport {
    public static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();
    public static final String CONTAINER_IMAGE = "quay.io/coreos/etcd:v2.3.7";
    public static final String CONTAINER_NAME = "etcd";
    public static final int ETCD_CLIENT_PORT = 2379;
    public static final int ETCD_PEER_PORT = 2380;

    protected static final Processor NODE_TO_VALUE_IN = exchange -> {
        EtcdKeysResponse response = exchange.getMessage().getBody(EtcdKeysResponse.class);
        if (response != null) {
            exchange.getMessage().setBody(response.node.key + "=" + response.node.value);
        }
    };

    protected String getClientUri() {
        return String.format(
            "http://%s:%d",
            getContainerHost(CONTAINER_NAME),
            getContainerPort(CONTAINER_NAME, ETCD_CLIENT_PORT));
    }

    protected EtcdClient getClient() {
        return new EtcdClient(URI.create(getClientUri()));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        EtcdKeysComponent keys = new EtcdKeysComponent();
        keys.getConfiguration().setUris(getClientUri());

        EtcdStatsComponent stats = new EtcdStatsComponent();
        stats.getConfiguration().setUris(getClientUri());

        EtcdWatchComponent watch = new EtcdWatchComponent();
        watch.getConfiguration().setUris(getClientUri());

        CamelContext context = super.createCamelContext();
        context.addComponent("etcd-keys", keys);
        context.addComponent("etcd-stats", stats);
        context.addComponent("etcd-watch", watch);

        return context;
    }

    @Override
    protected GenericContainer<?> createContainer() {
        return etcdContainer();
    }

    public static GenericContainer etcdContainer() {
        return new GenericContainer(CONTAINER_IMAGE)
            .withNetworkAliases(CONTAINER_NAME)
            .withExposedPorts(ETCD_CLIENT_PORT, ETCD_PEER_PORT)
            .waitingFor(Wait.forLogMessageContaining("etcdserver: set the initial cluster version", 1))
            .withCommand(
                "-name", CONTAINER_NAME + "-0",
                "-advertise-client-urls", "http://" + DockerClientFactory.instance().dockerHostIpAddress() + ":" + ETCD_CLIENT_PORT,
                "-listen-client-urls", "http://0.0.0.0:" + ETCD_CLIENT_PORT
            );
    }
}
