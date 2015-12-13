/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mllp;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpServerResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.apache.camel.test.Data.*;

public class MllpTcpClientProducerTest extends CamelTestSupport {
    @Rule
    public MllpServerResource mllpServer = new MllpServerResource( AvailablePortFinder.getNextAvailable() );

    String targetURI = "direct://mllp-sender";
    int deliveryDelay = 25;

    @EndpointInject(uri = "mock://response")
    MockEndpoint response;

    @EndpointInject(uri = "mock://timeout-ex")
    MockEndpoint timeout;

    @EndpointInject(uri = "mock://frame-ex")
    MockEndpoint frame;

    @Override
    public String isMockEndpoints() {
        return "log://netty-mllp-sender-throughput*";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        return registry;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            int connectTimeout = 1000;
            int responseTimeout = 1000;

            @Override
            public void configure() throws Exception {
                errorHandler(
                        defaultErrorHandler()
                                .allowRedeliveryWhileStopping(false)
                );
                /*
                onException(ReadTimeoutException.class, ConnectException.class)
                        .redeliveryDelay(2500)
//                .maximumRedeliveryDelay(15000)
//                .backOffMultiplier(2)
                        .maximumRedeliveries(-1)
                        .logContinued(true)
                        .logExhausted(true)
                        .logExhaustedMessageHistory(false)
                        .logHandled(true)
                        .logRetryAttempted(true)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .toF("log:%s-connect-ex?level=WARN", routeId)
                ;
                */

                /*
                onException(ReadTimeoutException.class)
                        .logHandled(true)
                        .handled(true)
                        .to("direct://handle-timeout")
                ;
                */

                onException(MllpCorruptFrameException.class)
                        .handled(true)
                        .logHandled(false)
                        .to("mock://frame-ex")
                ;

                onException(MllpTimeoutException.class)
                        .handled(true)
                        .logHandled(false)
                        .to("mock://timeout-ex")
                ;

                onCompletion().onFailureOnly().log(LoggingLevel.ERROR, "Processing Failed");

                from(targetURI)
                        .routeId("mllp-sender-test-route")
                        .log(LoggingLevel.INFO, "Sending Message: $simple{header[CamelHL7MessageControl]}")
                        .toF("mllp://%s:%d?connectTimeout=%d&responseTimeout=%d",
                                "0.0.0.0", mllpServer.getListenPort(), connectTimeout, responseTimeout)
                        .to("mock://response")
                ;

                from("direct://handle-timeout")
                        .log(LoggingLevel.ERROR, "Response Timeout")
                        .rollback()
                ;

            }
        };

    }

    @Test
    public void testSendSingleMessage() throws Exception {
        response.setExpectedMessageCount(1);
        timeout.setExpectedMessageCount(0);
        frame.setExpectedMessageCount(0);

        template.sendBody(targetURI, TEST_MESSAGE_1);

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


    @Test
    public void testSendMultipleMessages() throws Exception {
        int messageCount = 5;
        response.setExpectedMessageCount(messageCount);
        timeout.setExpectedMessageCount(0);
        frame.setExpectedMessageCount(0);

        for (int i = 0; i < messageCount; ++i) {
            template.sendBody(targetURI, TEST_MESSAGE_1);
            Thread.sleep(deliveryDelay);
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


    @Test
    public void testNoResponseOnFirstMessage() throws Exception {
        int sendMessageCount = 5;
        response.setExpectedMessageCount(sendMessageCount - 1);
        timeout.expectedMessageCount(1);
        frame.setExpectedMessageCount(0);

        NotifyBuilder notify1 = new NotifyBuilder(context).whenDone(1).create();
        NotifyBuilder notify2 = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.disableResponse();

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(TEST_MESSAGE_1);
        assertTrue("Notify not completed", notify1.matches(10, TimeUnit.SECONDS));
        mllpServer.enableResponse();

        template.sendBody(TEST_MESSAGE_2);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_3);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_4);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_5);
        Thread.sleep(deliveryDelay);

        assertTrue("Notify not completed", notify2.matches(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testNoResponseOnNthMessage() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);
        timeout.expectedMessageCount(1);
        frame.setExpectedMessageCount(0);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.disableResponse(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(TEST_MESSAGE_1);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_2);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_3);
        Thread.sleep(deliveryDelay);

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testMissingEndOfDataByte() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.setExcludeEndOfDataModulus(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(TEST_MESSAGE_1);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_2);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_3);
        Thread.sleep(deliveryDelay);

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testMissingEndOfBlockByte() throws Exception {
        int sendMessageCount = 3;
        response.setExpectedMessageCount(sendMessageCount - 1);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(sendMessageCount).create();

        mllpServer.setExcludeEndOfBlockModulus(sendMessageCount);

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(TEST_MESSAGE_1);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_2);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_3);
        Thread.sleep(deliveryDelay);

        assertTrue("Notify not completed", notify.matches(sendMessageCount, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testApplicationAcceptAcknowledgement() throws Exception {
        int messageCount = 5;
        response.setExpectedMessageCount(messageCount);

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(messageCount).create();

        template.setDefaultEndpointUri(targetURI);
        template.sendBody(TEST_MESSAGE_1);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_2);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_3);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_4);
        Thread.sleep(deliveryDelay);

        template.sendBody(TEST_MESSAGE_5);
        Thread.sleep(deliveryDelay);

        assertTrue("Notify not completed", notify.matches(5, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

}