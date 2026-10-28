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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppMessageType;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.examples.SMPPServerSimulator;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class SmppTRXProducerIT extends CamelTestSupport {

    @RegisterExtension
    static AvailablePortFinder.Port PORT = AvailablePortFinder.find();
    private static Thread smppServerSimulator;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("direct:start")
    private Endpoint start;

    @EndpointInject("mock:result2")
    private MockEndpoint result2;

    @EndpointInject("direct:start2")
    private Endpoint start2;

    @BeforeAll
    public static void runSMPPServerSimulator() {
        smppServerSimulator = new Thread(() -> {
            System.setProperty("jsmpp.simulator.port", "" + PORT.getPort());
            SMPPServerSimulator.main(null);
        });
        smppServerSimulator.setDaemon(true);
        smppServerSimulator.start();

        // Wait for the SMPP server to be ready before the Camel context starts,
        // otherwise the route startup connection attempt fails with "Connection refused".
        // A BIND_TX (transmitter) bind is used as the readiness probe: the simulator only
        // accepts BIND_TRX (transceiver) binds, so it rejects this immediately with a
        // negative response code. That rejection proves the server's accept loop is running
        // without blocking it (a raw TCP probe would stall the simulator for 30 s waiting
        // for a bind PDU). Any IOException means the server is not yet listening.
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            try (SMPPSession probe = new SMPPSession()) {
                probe.connectAndBind("localhost", PORT.getPort(),
                        new BindParameter(
                                BindType.BIND_TX, "j", "jpwd", null,
                                TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));
                return true;
            } catch (Exception e) {
                // connectAndBind wraps NegativeResponseException in IOException.
                // A NegativeResponseException cause means the server is up and rejected
                // BIND_TX as expected (it only accepts BIND_TRX).
                // Any other exception (e.g. ConnectException) means not yet ready.
                return e.getCause() instanceof org.jsmpp.extra.NegativeResponseException;
            }
        });
    }

    @AfterAll
    public static void stopSMPPServerSimulator() {
        if (smppServerSimulator != null) {
            smppServerSimulator.interrupt();
        }
    }

    @Test
    void sendSubmitSMInOnly() throws Exception {
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
    }

    @Test
    void testTypoInMessageReceiverRouteId() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start2")
                            .to("smpp://j@localhost:" + PORT.getPort() + "?password=jpwd&systemType=producer" +
                                "&messageReceiverRouteId=TYPO_IN_MessageReceiverRouteId");
                }
            });

            fail("FailedToStartRouteException expected!");

        } catch (FailedToStartRouteException expected) {
            assertEquals("java.lang.IllegalArgumentException:" +
                         " No route with id 'TYPO_IN_MessageReceiverRouteId' found!",
                    expected.getCause().getMessage());
        }
    }

    @Test
    void testFindRouteAfterStartupToo() throws Exception {
        result2.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start2")
                        .to("smpp://j@localhost:" + PORT.getPort() + "?password=jpwd&systemType=producer" +
                            "&messageReceiverRouteId=testMessageReceiverRouteId2");
            }
        });

        Exchange exchange = start2.createExchange(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start2, exchange);

        MockEndpoint.assertIsSatisfied(context);
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

                from("direct:messageReceiver2").id("testMessageReceiverRouteId2")
                        .to("mock:result2");
            }
        };
    }
}
