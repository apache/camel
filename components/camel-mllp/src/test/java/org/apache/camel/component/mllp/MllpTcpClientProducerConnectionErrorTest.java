/**
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
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Rule;
import org.junit.Test;

import static org.apache.camel.test.mllp.Hl7MessageGenerator.generateMessage;

public class MllpTcpClientProducerConnectionErrorTest extends CamelTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource("localhost", AvailablePortFinder.getNextAvailable());

    @EndpointInject(uri = "direct://source")
    ProducerTemplate source;

    @EndpointInject(uri = "mock://complete")
    MockEndpoint complete;

    @EndpointInject(uri = "mock://write-ex")
    MockEndpoint writeEx;

    @EndpointInject(uri = "mock://receive-ex")
    MockEndpoint receiveEx;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String routeId = "mllp-sender";

            public void configure() {
                onException(MllpWriteException.class)
                        .handled(true)
                        .to(writeEx)
                        .log(LoggingLevel.ERROR, routeId, "Write Error")
                        .stop();

                onException(MllpReceiveAcknowledgementException.class)
                        .handled(true)
                        .to(receiveEx)
                        .log(LoggingLevel.ERROR, routeId, "Receive Error")
                        .stop();

                from(source.getDefaultEndpoint()).routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Sending Message")
                        .toF("mllp://%s:%d", mllpServer.getListenHost(), mllpServer.getListenPort())
                        .log(LoggingLevel.INFO, routeId, "Received Acknowledgement")
                        .to(complete);
            }
        };
    }

    @Test
    public void testConnectionClosedBeforeSendingHL7Message() throws Exception {
        complete.expectedMessageCount(1);
        writeEx.expectedMessageCount(0);
        receiveEx.expectedMessageCount(1);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(2).create();

        // Need to send one message to get the connection established
        source.sendBody(generateMessage());

        mllpServer.closeClientConnections();
        source.sendBody(generateMessage());

        assertTrue("Should have completed an exchange", done.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Test()
    public void testConnectionResetBeforeSendingHL7Message() throws Exception {
        complete.expectedMessageCount(1);
        writeEx.expectedMessageCount(1);
        receiveEx.expectedMessageCount(0);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(2).create();

        // Need to send one message to get the connection established
        source.sendBody(generateMessage());

        mllpServer.resetClientConnections();

        source.sendBody(generateMessage());

        assertTrue("Should have completed an exchange", done.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Test()
    public void testConnectionClosedBeforeReadingAcknowledgement() throws Exception {
        complete.expectedMessageCount(0);
        writeEx.expectedMessageCount(0);
        receiveEx.expectedMessageCount(1);

        mllpServer.setCloseSocketBeforeAcknowledgementModulus(1);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(1).create();

        source.sendBody(generateMessage());

        assertTrue("Should have completed an exchange", done.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

    @Test()
    public void testConnectionResetBeforeReadingAcknowledgement() throws Exception {
        complete.expectedMessageCount(0);
        writeEx.expectedMessageCount(0);
        receiveEx.expectedMessageCount(1);

        mllpServer.setResetSocketBeforeAcknowledgementModulus(1);

        NotifyBuilder done = new NotifyBuilder(context).whenCompleted(1).create();

        source.sendBody(generateMessage());

        assertTrue("Should have completed an exchange", done.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }

}
