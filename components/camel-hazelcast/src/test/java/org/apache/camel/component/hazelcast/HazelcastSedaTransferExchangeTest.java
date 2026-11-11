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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.apache.camel.test.infra.hazelcast.services.HazelcastService;
import org.apache.camel.test.infra.hazelcast.services.HazelcastServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HazelcastSedaTransferExchangeTest extends CamelTestSupport {

    @RegisterExtension
    public static HazelcastService hazelcastService = HazelcastServiceFactory.createService();

    @RegisterExtension
    public static TestEntityNameGenerator nameGenerator = new TestEntityNameGenerator();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @EndpointInject("mock:transferred")
    private MockEndpoint transferred;

    @EndpointInject("mock:bodyOnly")
    private MockEndpoint bodyOnly;

    private HazelcastInstance hazelcastInstance;

    @BeforeAll
    public void beforeAll() {
        hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastService.createConfiguration(null, 0, null, "seda"));
    }

    @AfterEach
    public void afterEach() {
        mock.reset();
        transferred.reset();
        bodyOnly.reset();
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

        transferred.expectedMessageCount(1);
        transferred.expectedBodiesReceived("test");
        transferred.expectedHeaderReceived("test", value);
        transferred.expectedPropertyReceived("test", value);

        Exchange exchange = createExchangeWithBody("test");
        exchange.getIn().setHeader("test", value);
        exchange.setProperty("test", value);

        template.send("direct:foobar", exchange);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void transferredExchangeGivesOnlyItsBodyToAConsumerWithoutTransferExchange() throws InterruptedException {
        bodyOnly.expectedMessageCount(1);
        bodyOnly.expectedBodiesReceived("test");
        bodyOnly.expectedNoHeaderReceived();

        Exchange exchange = createExchangeWithBody("test");
        exchange.getIn().setHeader("test", "Not propagated");
        exchange.setProperty("test", "Not propagated");

        template.send("direct:bodyOnly", exchange);

        MockEndpoint.assertIsSatisfied(context);
        assertNull(bodyOnly.getReceivedExchanges().get(0).getProperty("test"));
    }

    @Test
    void holderOnTheQueueGivesOnlyItsBodyWithoutTransferExchange() throws InterruptedException {
        bodyOnly.expectedMessageCount(1);
        bodyOnly.expectedBodiesReceived("held");
        bodyOnly.expectedNoHeaderReceived();

        hazelcastInstance.getQueue("body-only").add(holder("held"));

        MockEndpoint.assertIsSatisfied(context);
        Exchange received = bodyOnly.getReceivedExchanges().get(0);
        assertNull(received.getProperty("test"));
        assertNull(received.getException());
    }

    @Test
    void holderOnTheQueueGivesItsHeadersAndPropertiesWithTransferExchange() throws InterruptedException {
        transferred.expectedMessageCount(1);
        transferred.expectedBodiesReceived("held");
        transferred.expectedHeaderReceived("test", "held header");
        transferred.expectedPropertyReceived("test", "held property");

        hazelcastInstance.getQueue("transferred").add(holder("held"));

        MockEndpoint.assertIsSatisfied(context);
    }

    private DefaultExchangeHolder holder(String body) {
        Exchange exchange = createExchangeWithBody(body);
        exchange.getIn().setHeader("test", "held header");
        exchange.setProperty("test", "held property");
        return DefaultExchangeHolder.marshal(exchange);
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

                from("hazelcast-seda:foo").to("mock:result");

                // transferExchange on both sides: the headers and exchange properties come along
                from("direct:foobar").to("hazelcast-seda:transferred?transferExchange=true");

                from("hazelcast-seda:transferred?transferExchange=true").to("mock:transferred");

                // transferExchange only on the producer: the consumer takes only the body
                from("direct:bodyOnly").to("hazelcast-seda:body-only?transferExchange=true");

                from("hazelcast-seda:body-only").to("mock:bodyOnly");
            }
        };
    }
}
