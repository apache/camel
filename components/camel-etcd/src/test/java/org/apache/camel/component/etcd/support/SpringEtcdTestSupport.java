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
import org.apache.camel.CamelContext;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.component.etcd.EtcdKeysComponent;
import org.apache.camel.component.etcd.EtcdStatsComponent;
import org.apache.camel.component.etcd.EtcdWatchComponent;
import org.apache.camel.test.testcontainers.spring.junit5.ContainerAwareSpringTestSupport;
import org.testcontainers.containers.GenericContainer;

public abstract class SpringEtcdTestSupport extends ContainerAwareSpringTestSupport {
    protected static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();
    protected static final EtcdConfiguration CONFIGURATION = new EtcdConfiguration();

    @Override
    protected GenericContainer<?> createContainer() {
        return EtcdTestSupport.etcdContainer();
    }

    protected String getClientUri() {
        return String.format(
            "http://%s:%d",
            getContainerHost(EtcdTestSupport.CONTAINER_NAME),
            getContainerPort(EtcdTestSupport.CONTAINER_NAME, EtcdTestSupport.ETCD_CLIENT_PORT));
    }

    protected EtcdClient getClient() {
        return new EtcdClient(URI.create(getClientUri()));
    }

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

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
}
