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

package org.apache.camel.component.tahu;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.tahu.handlers.TahuEdgeClient;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultContextLifeCycleManager;
import org.apache.camel.test.infra.core.TransientCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TahuTestSupport implements ConfigurableContext, ConfigurableRoute {

    private static final Logger LOG = LoggerFactory.getLogger(TahuTestSupport.class);

    @Order(1)
    @RegisterExtension
    protected static final SparkplugTCKService spTckService = new SparkplugTCKService();

    @Order(2)
    @RegisterExtension
    protected static final CamelContextExtension camelContextExtension =
            new TransientCamelContextExtension(new DefaultContextLifeCycleManager() {
                @Override
                public void beforeEach(CamelContext context) {
                    // NO-OP - Let test methods start the context
                }
            });

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) {
        String containerAddress = SparkplugTCKService.getMqttHostAddress();

        TahuConfiguration tahuConfig = new TahuConfiguration();

        tahuConfig.setServers("Mqtt Server One:" + containerAddress);
        tahuConfig.setClientId("CamelTestClient");
        tahuConfig.setCheckClientIdLength(false);
        tahuConfig.setUsername("admin");
        tahuConfig.setPassword("changeme");

        configureComponent(context, tahuConfig);
    }

    protected abstract void configureComponent(CamelContext context, TahuConfiguration tahuConfig);

    void startContext() throws Exception {
        CamelContext camelContext = camelContextExtension.getContext();
        camelContext.start();
        Awaitility.await().atMost(10L, TimeUnit.SECONDS).until(camelContext::isStarted);
    }

    void stopContext() throws Exception {
        TahuEdgeProducer.descriptorFutures.forEach((end, future) -> {
            TahuEdgeClient client = TahuEdgeProducer.descriptorClients.remove(end);
            client.shutdown();
            try {
                future.get(5L, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warn("Exception caught trying to shut down TahuEdgeClient", e);
            }
        });

        CamelContext camelContext = camelContextExtension.getContext();
        camelContext.stop();
        Awaitility.await().atMost(10L, TimeUnit.SECONDS).until(camelContext::isStopped);
    }

    @BeforeAll
    public static void beforeAll() throws Exception {
        Awaitility.await().atMost(10L, TimeUnit.SECONDS).until(spTckService::isConnected);
    }

    @AfterEach
    public void afterEach() throws Exception {
        spTckService.resetTckTest();

        stopContext();
    }
}
