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

import java.util.function.Consumer;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testing the monitored items of consumers sharing a connection, when a consumer is restarted or its node does not
 * exist
 */
public class MonitorItemLifecycleTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    // the client endpoints only differ in the node and the consumer options, so they share one connection
    private static final String MILO_CLIENT = "milo-client:opc.tcp://foo:bar@localhost:@@port@@"
                                              + "?allowedSecurityPolicies=None&overrideHost=true"
                                              + "&samplingInterval=50&requestedPublishingInterval=100&node=";

    private static final String MILO_CLIENT_ITEM_1
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1");

    private static final String MILO_CLIENT_UNKNOWN_ITEM
            = MILO_CLIENT + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItem")
              + "&omitNullValues=false";

    private static final String MOCK_ITEM_1 = "mock:item1";

    private static final String MOCK_UNKNOWN_ITEM = "mock:unknownItem";

    @EndpointInject(MOCK_ITEM_1)
    protected MockEndpoint item1Endpoint;

    @EndpointInject(MOCK_UNKNOWN_ITEM)
    protected MockEndpoint unknownItemEndpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                from(resolve(MILO_CLIENT_ITEM_1)).routeId("item1").to(MOCK_ITEM_1);
                from(resolve(MILO_CLIENT_UNKNOWN_ITEM)).routeId("unknownItem").to(MOCK_UNKNOWN_ITEM);
            }
        };
    }

    @Test
    void restartedConsumerReceivesEachValueOnce() throws Exception {
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        producer1.sendBody("Foo");
        item1Endpoint.assertIsSatisfied();

        // the other consumers keep the shared connection open
        context.getRouteController().stopRoute("item1");

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(0);
        item1Endpoint.setAssertPeriod(1000);
        producer1.sendBody("Bar");
        item1Endpoint.assertIsSatisfied();

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Bar"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        item1Endpoint.reset();
        item1Endpoint.expectedMessageCount(1);
        item1Endpoint.setAssertPeriod(1000);
        testBody(item1Endpoint.message(0), assertGoodValue("Baz"));
        producer1.sendBody("Baz");
        item1Endpoint.assertIsSatisfied();
    }

    @Test
    void unknownNodeReceivesBadStatus() throws Exception {
        unknownItemEndpoint.expectedMinimumMessageCount(1);
        testBody(unknownItemEndpoint.message(0), assertStatus(StatusCodes.Bad_NodeIdUnknown));
        unknownItemEndpoint.assertIsSatisfied();

        // the other consumers of the connection are not affected
        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        producer1.sendBody("Foo");
        item1Endpoint.assertIsSatisfied();
    }

    private static Consumer<DataValue> assertStatus(final long statusCode) {
        return value -> assertEquals(new StatusCode(statusCode), value.getStatusCode());
    }
}
