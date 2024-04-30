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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class MllpTcpClientProducerIdleConnectionTimeoutTest extends CamelTestSupport {

    static final int CONNECT_TIMEOUT = 500;
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;
    static final int IDLE_TIMEOUT = RECEIVE_TIMEOUT * 3;

    Logger log = LoggerFactory.getLogger(MllpTcpClientProducerIdleConnectionTimeoutTest.class);

    @RegisterExtension
    MllpServerResource mllpServer = new MllpServerResource("localhost", AvailablePortFinder.getNextAvailable());

    @EndpointInject("direct://source")
    ProducerTemplate source;

    @EndpointInject("mock://complete")
    MockEndpoint complete;

    @EndpointInject("mock://write-ex")
    MockEndpoint writeEx;

    @EndpointInject("mock://receive-ex")
    MockEndpoint receiveEx;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            public void configure() {
                onException(MllpWriteException.class)
                        .handled(true)
                        .to(writeEx)
                        .log(LoggingLevel.ERROR, routeId, "Write Error")
                        .stop();

                onException(MllpAcknowledgementReceiveException.class)
                        .handled(true)
                        .to(receiveEx)
                        .log(LoggingLevel.ERROR, routeId, "Receive Error")
                        .stop();

                from(source.getDefaultEndpoint()).routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d?connectTimeout=%d&receiveTimeout=%d&readTimeout=%d&idleTimeout=%s",
                                mllpServer.getListenHost(), mllpServer.getListenPort(),
                                CONNECT_TIMEOUT, RECEIVE_TIMEOUT, READ_TIMEOUT, IDLE_TIMEOUT)
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to(complete);
            }
        };
    }

    @Test
    public void testIdleConnectionTimeout() throws Exception {
        complete.expectedMessageCount(2);
        writeEx.expectedMessageCount(0);
        receiveEx.expectedMessageCount(0);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(2).create();

        // Need to send one message to get the connection established
        source.sendBody(Hl7TestMessageGenerator.generateMessage());

        Thread.sleep(IDLE_TIMEOUT / 2);
        source.sendBody(Hl7TestMessageGenerator.generateMessage());

        assertTrue(done.matches(5, TimeUnit.SECONDS), "Should have completed two exchanges");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        Thread.sleep((long) (IDLE_TIMEOUT * 1.1));

        assertThrows(MllpJUnitResourceException.class,
                () -> mllpServer.checkClientConnections());
    }

    @Test
    public void testReconnectAfterIdleConnectionTimeout() throws Exception {
        complete.expectedMessageCount(3);
        writeEx.expectedMessageCount(0);
        receiveEx.expectedMessageCount(0);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(2).create();

        // Need to send one message to get the connection established
        source.sendBody(Hl7TestMessageGenerator.generateMessage());

        Thread.sleep(IDLE_TIMEOUT / 2);
        source.sendBody(Hl7TestMessageGenerator.generateMessage());

        assertTrue(done.matches(5, TimeUnit.SECONDS), "Should have completed two exchanges");

        Thread.sleep((long) (IDLE_TIMEOUT * 1.1));

        try {
            mllpServer.checkClientConnections();
            fail("Should receive and exception for the closed connection");
        } catch (MllpJUnitResourceException expectedEx) {
            // Eat this
        }

        source.sendBody(Hl7TestMessageGenerator.generateMessage());

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        log.debug("Breakpoint");
    }
}
