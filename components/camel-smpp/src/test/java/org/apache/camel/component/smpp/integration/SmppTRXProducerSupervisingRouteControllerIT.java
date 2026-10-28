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
package org.apache.camel.component.smpp.integration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppMessageType;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jsmpp.examples.SMPPServerSimulator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Isolated;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class SmppTRXProducerSupervisingRouteControllerIT extends CamelTestSupport {
    @RegisterExtension
    static AvailablePortFinder.Port PORT = AvailablePortFinder.find();
    private static Thread smppServerSimulator;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("direct:start")
    private Endpoint start;

    private List<CamelEvent.RouteRestartingEvent> events;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(500);
        src.setBackOffMaxAttempts(20);
        src.setInitialDelay(100);
        src.setThreadPoolSize(2);

        events = new CopyOnWriteArrayList<>();
        context.getManagementStrategy().addEventNotifier(new SimpleEventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                if (event instanceof CamelEvent.RouteRestartingEvent rre) {
                    events.add(rre);
                }
            }
        });

        return context;
    }

    private static void runSMPPServerSimulator() {
        // a new thread is created each time, as the test class can be re-run in the same JVM
        smppServerSimulator = new Thread(() -> {
            System.setProperty("jsmpp.simulator.port", "" + PORT.getPort());
            SMPPServerSimulator.main(null);
        });
        smppServerSimulator.setDaemon(true);
        smppServerSimulator.start();
    }

    @AfterAll
    public static void stopSMPPServerSimulator() {
        if (smppServerSimulator != null) {
            smppServerSimulator.interrupt();
        }
    }

    @Test
    void sendSubmitSMInOnly() throws Exception {
        // the SMPP server is not available yet, so the supervising route controller must be restarting the route
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(events.size() >= 3,
                        "There should be restarting events, size: " + events.size()));

        runSMPPServerSimulator();

        result.expectedMessageCount(1);

        Exchange exchange = start.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start, exchange);

        MockEndpoint.assertIsSatisfied(context);
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), resultExchange.getIn().getHeader(SmppConstants.MESSAGE_TYPE));
        assertEquals("Hello SMPP World!", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.SUBMITTED));
        assertEquals(1, resultExchange.getIn().getHeader(SmppConstants.DELIVERED));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.DONE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(SmppConstants.SUBMIT_DATE));
        assertNull(resultExchange.getIn().getHeader(SmppConstants.ERROR));

        assertNotNull(exchange.getIn().getHeader(SmppConstants.ID));
        assertEquals(1, exchange.getIn().getHeader(SmppConstants.SENT_MESSAGE_COUNT));

        // there should be some restart events
        assertTrue(events.size() >= 3, "There should be restarting events, size: " + events.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("smpp://j@localhost:" + PORT.getPort() + "?password=jpwd&systemType=producer" +
                            "&messageReceiverRouteId=testMessageReceiverRouteId");

                from("direct:messageReceiver").id("testMessageReceiverRouteId")
                        .choice()
                        .when(simple("${header.CamelSmppSourceAddr} == '555'"))
                        .to("mock:garbage") // SMPPServerSimulator.run send a test message, ignore it
                        .otherwise()
                        .to("mock:result");
            }
        };
    }
}
