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
package org.apache.camel.component.activemq6;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The connection factories that the component creates from the broker URL keep working when the component, a route or
 * the CamelContext is stopped and started again, and their connections are closed when Camel stops.
 */
public class ActiveMQComponentRestartTest {

    private BrokerService broker;
    private String brokerUrl;

    @BeforeEach
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("restart");
        broker.setPersistent(false);
        broker.setUseJmx(true);
        broker.getManagementContext().setCreateConnector(false);
        broker.setAdvisorySupport(false);
        TransportConnector connector = broker.addConnector("tcp://localhost:0");
        broker.start();
        broker.waitUntilStarted();
        brokerUrl = connector.getConnectUri().toString();
    }

    @AfterEach
    void stopBroker() throws Exception {
        broker.stop();
    }

    private long brokerConnections() throws Exception {
        return broker.getAdminView().getCurrentConnectionsCount();
    }

    private ActiveMQComponent activemq(boolean singleConnection) {
        ActiveMQComponent activemq = new ActiveMQComponent();
        activemq.setBrokerURL(brokerUrl);
        activemq.setUseSingleConnection(singleConnection);
        return activemq;
    }

    private static CamelContext context(ActiveMQComponent activemq, String queue) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("activemq", activemq);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:" + queue).routeId("consumer").to("mock:result");
            }
        });
        return context;
    }

    private static void assertSendAndReceive(CamelContext context, String queue, String body) throws Exception {
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.reset();
        result.expectedBodiesReceived(body);
        context.createProducerTemplate().sendBody("activemq:queue:" + queue, body);
        MockEndpoint.assertIsSatisfied(context);
    }

    private void awaitNoBrokerConnections() {
        await().atMost(10, TimeUnit.SECONDS).until(() -> brokerConnections() == 0);
    }

    @Test
    public void componentStopAndStartWithPooledConnection() throws Exception {
        componentStopAndStart(false, "restart.component.pooled");
    }

    @Test
    public void componentStopAndStartWithSingleConnection() throws Exception {
        componentStopAndStart(true, "restart.component.single");
    }

    private void componentStopAndStart(boolean singleConnection, String queue) throws Exception {
        ActiveMQComponent activemq = activemq(singleConnection);
        CamelContext context = context(activemq, queue);
        context.start();
        try {
            assertSendAndReceive(context, queue, "before");

            context.getRouteController().stopRoute("consumer");
            activemq.stop();
            awaitNoBrokerConnections();
            activemq.start();
            context.getRouteController().startRoute("consumer");

            assertSendAndReceive(context, queue, "after");

            // and the connections opened after the restart are closed when the component stops again
            context.getRouteController().stopRoute("consumer");
            activemq.stop();
            awaitNoBrokerConnections();
        } finally {
            context.stop();
        }
    }

    @Test
    public void routeStopAndStart() throws Exception {
        String queue = "restart.route";
        CamelContext context = context(activemq(false), queue);
        context.start();
        try {
            assertSendAndReceive(context, queue, "before");

            context.getRouteController().stopRoute("consumer");
            context.getRouteController().startRoute("consumer");

            assertSendAndReceive(context, queue, "after");
        } finally {
            context.stop();
        }
        awaitNoBrokerConnections();
    }

    @Test
    public void contextStopAndStart() throws Exception {
        String queue = "restart.context";
        ActiveMQComponent activemq = activemq(false);
        CamelContext context = context(activemq, queue);
        context.start();
        try {
            assertSendAndReceive(context, queue, "before");

            // the CamelContext shuts its components down when it stops, and creates its endpoints again when it starts
            context.stop();
            awaitNoBrokerConnections();
            context.addComponent("activemq", activemq);
            context.start();

            assertSendAndReceive(context, queue, "after");
        } finally {
            context.stop();
        }
        awaitNoBrokerConnections();
    }

    @Test
    public void shutdownStopsThePools() throws Exception {
        String queue = "restart.shutdown";
        CamelContext context = context(activemq(false), queue);
        context.start();
        JmsPoolConnectionFactory pool;
        try {
            assertSendAndReceive(context, queue, "Hello");
            pool = assertInstanceOf(JmsPoolConnectionFactory.class,
                    context.getEndpoint("activemq:queue:" + queue, JmsEndpoint.class).getConfiguration()
                            .getConnectionFactory());
        } finally {
            context.stop();
        }
        awaitNoBrokerConnections();
        // a stopped pool gives no connection
        assertNull(pool.createConnection());
    }
}
