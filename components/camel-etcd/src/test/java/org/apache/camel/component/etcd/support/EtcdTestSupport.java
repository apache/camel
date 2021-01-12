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
import org.apache.camel.test.infra.etcd.services.EtcDService;
import org.apache.camel.test.infra.etcd.services.EtcDServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class EtcdTestSupport extends CamelTestSupport {
    @RegisterExtension
    public static EtcDService service = EtcDServiceFactory.createService();

    public static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();

    protected static final Processor NODE_TO_VALUE_IN = exchange -> {
        EtcdKeysResponse response = exchange.getMessage().getBody(EtcdKeysResponse.class);
        if (response != null) {
            exchange.getMessage().setBody(response.node.key + "=" + response.node.value);
        }
    };

    protected EtcdClient getClient() {
        return new EtcdClient(URI.create(service.getServiceAddress()));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        EtcdKeysComponent keys = new EtcdKeysComponent();
        keys.getConfiguration().setUris(service.getServiceAddress());

        EtcdStatsComponent stats = new EtcdStatsComponent();
        stats.getConfiguration().setUris(service.getServiceAddress());

        EtcdWatchComponent watch = new EtcdWatchComponent();
        watch.getConfiguration().setUris(service.getServiceAddress());

        CamelContext context = super.createCamelContext();
        context.addComponent("etcd-keys", keys);
        context.addComponent("etcd-stats", stats);
        context.addComponent("etcd-watch", watch);

        return context;
    }
}
