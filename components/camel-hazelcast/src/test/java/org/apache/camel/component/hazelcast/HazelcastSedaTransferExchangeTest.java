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
package org.apache.camel.component.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HazelcastSedaTransferExchangeTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    private HazelcastInstance hazelcastInstance;

    @BeforeAll
    public void beforeAll() {
        Config config = new Config();
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    public void afterEach() {
        mock.reset();
    }

    @AfterAll
    public void afterAll() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    public void testExchangeTransferEnabled() throws InterruptedException {
        final String value = "CAMEL-3983";

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedHeaderReceived("test", value);

        Exchange exchange = createExchangeWithBody("test");
        exchange.getIn().setHeader("test", value);

        template.send("direct:foobar", exchange);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testExchangeTransferDisabled() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedNoHeaderReceived();

        Exchange exchange = createExchangeWithBody("test");
        exchange.getIn().setHeader("test", "Not propagated");

        template.send("direct:foo", exchange);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HazelcastCamelTestHelper.registerHazelcastComponents(context, hazelcastInstance);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").to("hazelcast-seda:foo");

                from("direct:foobar").to("hazelcast-seda:foo?transferExchange=true");

                from("hazelcast-seda:foo").to("mock:result");
            }
        };
    }
}
