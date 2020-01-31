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
package org.apache.camel.component.milo;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.Test;

import static org.apache.camel.component.milo.NodeIds.nodeValue;

/**
 * Unit tests for writing from the client side
 */
public class WriteClientTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";
    private static final String DIRECT_START_2 = "direct:start2";
    private static final String DIRECT_START_3 = "direct:start3";
    private static final String DIRECT_START_4 = "direct:start4";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";
    private static final String MILO_SERVER_ITEM_2 = "milo-server:myitem2";

    private static final String MILO_CLIENT_BASE_C1 = "milo-client:opc.tcp://foo:bar@localhost:@@port@@";
    private static final String MILO_CLIENT_BASE_C2 = "milo-client:opc.tcp://foo2:bar2@localhost:@@port@@";

    private static final String MILO_CLIENT_ITEM_C1_1 = MILO_CLIENT_BASE_C1 + "?node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem1")
                                                        + "&overrideHost=true";
    private static final String MILO_CLIENT_ITEM_C1_2 = MILO_CLIENT_BASE_C1 + "?node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem2")
                                                        + "&overrideHost=true";

    private static final String MILO_CLIENT_ITEM_C2_1 = MILO_CLIENT_BASE_C2 + "?node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem1")
                                                        + "&overrideHost=true";
    private static final String MILO_CLIENT_ITEM_C2_2 = MILO_CLIENT_BASE_C2 + "?node=" + nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "items-myitem2")
                                                        + "&overrideHost=true";

    private static final String MOCK_TEST_1 = "mock:test1";
    private static final String MOCK_TEST_2 = "mock:test2";

    @EndpointInject(MOCK_TEST_1)
    protected MockEndpoint test1Endpoint;

    @EndpointInject(MOCK_TEST_2)
    protected MockEndpoint test2Endpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Produce(DIRECT_START_2)
    protected ProducerTemplate producer2;

    @Produce(DIRECT_START_3)
    protected ProducerTemplate producer3;

    @Produce(DIRECT_START_4)
    protected ProducerTemplate producer4;

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from(MILO_SERVER_ITEM_1).to(MOCK_TEST_1);
                from(MILO_SERVER_ITEM_2).to(MOCK_TEST_2);

                from(DIRECT_START_1).to(resolve(MILO_CLIENT_ITEM_C1_1));
                from(DIRECT_START_2).to(resolve(MILO_CLIENT_ITEM_C1_2));

                from(DIRECT_START_3).to(resolve(MILO_CLIENT_ITEM_C2_1));
                from(DIRECT_START_4).to(resolve(MILO_CLIENT_ITEM_C2_2));
            }
        };
    }

    @Test
    public void testWrite1() throws Exception {
        // item 1
        this.test1Endpoint.setExpectedCount(2);
        testBody(this.test1Endpoint.message(0), assertGoodValue("Foo1"));
        testBody(this.test1Endpoint.message(1), assertGoodValue("Foo2"));

        // item 2
        this.test2Endpoint.setExpectedCount(0);

        // send
        sendValue(this.producer1, new Variant("Foo1"));
        sendValue(this.producer1, new Variant("Foo2"));

        // assert
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWrite2() throws Exception {
        // item 1
        this.test1Endpoint.setExpectedCount(0);

        // item 2
        this.test2Endpoint.setExpectedCount(2);
        testBody(this.test2Endpoint.message(0), assertGoodValue("Foo1"));
        testBody(this.test2Endpoint.message(1), assertGoodValue("Foo2"));

        // send
        sendValue(this.producer2, new Variant("Foo1"));
        sendValue(this.producer2, new Variant("Foo2"));

        // assert
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWrite3() throws Exception {
        // item 1
        this.test1Endpoint.setExpectedCount(2);
        testBody(this.test1Endpoint.message(0), assertGoodValue("Foo1"));
        testBody(this.test1Endpoint.message(1), assertGoodValue("Foo3"));

        // item 1
        this.test2Endpoint.setExpectedCount(2);
        testBody(this.test2Endpoint.message(0), assertGoodValue("Foo2"));
        testBody(this.test2Endpoint.message(1), assertGoodValue("Foo4"));

        // send
        sendValue(this.producer1, new Variant("Foo1"));
        sendValue(this.producer2, new Variant("Foo2"));
        sendValue(this.producer3, new Variant("Foo3"));
        sendValue(this.producer4, new Variant("Foo4"));

        // assert
        assertMockEndpointsSatisfied();
    }

    private static void sendValue(final ProducerTemplate producerTemplate, final Variant variant) {
        // we always write synchronously since we do need the message order
        producerTemplate.sendBodyAndHeader(variant, "await", true);
    }
}
