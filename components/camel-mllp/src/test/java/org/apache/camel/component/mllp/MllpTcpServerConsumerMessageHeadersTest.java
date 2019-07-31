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
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpServerConsumerMessageHeadersTest extends CamelTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @EndpointInject("mock://on-completion-result")
    MockEndpoint onCompletionResult;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        super.doPreSetup();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Test
    public void testHl7HeadersEnabled() throws Exception {
        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';

        addTestRoute(true);

        result.expectedMessageCount(1);

        result.expectedHeaderReceived(MllpConstants.MLLP_SENDING_APPLICATION, "ADT");
        result.expectedHeaderReceived(MllpConstants.MLLP_SENDING_FACILITY, "EPIC");
        result.expectedHeaderReceived(MllpConstants.MLLP_RECEIVING_APPLICATION, "JCAPS");
        result.expectedHeaderReceived(MllpConstants.MLLP_TIMESTAMP, "20160902123950");
        result.expectedHeaderReceived(MllpConstants.MLLP_SECURITY, "RISTECH");
        result.expectedHeaderReceived(MllpConstants.MLLP_MESSAGE_TYPE, "ADT^A08");
        result.expectedHeaderReceived(MllpConstants.MLLP_EVENT_TYPE, "ADT");
        result.expectedHeaderReceived(MllpConstants.MLLP_TRIGGER_EVENT, "A08");
        result.expectedHeaderReceived(MllpConstants.MLLP_MESSAGE_CONTROL, "00001");
        result.expectedHeaderReceived(MllpConstants.MLLP_PROCESSING_ID, "D");
        result.expectedHeaderReceived(MllpConstants.MLLP_VERSION_ID, "2.3");

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        Message message = result.getExchanges().get(0).getIn();

        assertNotNull("Should have header" + MllpConstants.MLLP_LOCAL_ADDRESS, message.getHeader(MllpConstants.MLLP_LOCAL_ADDRESS));
        assertNotNull("Should have header" + MllpConstants.MLLP_REMOTE_ADDRESS, message.getHeader(MllpConstants.MLLP_REMOTE_ADDRESS));
    }


    @Test
    public void testHl7HeadersDisabled() throws Exception {
        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';

        addTestRoute(false);

        result.expectedMessageCount(1);

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        Message message = result.getExchanges().get(0).getIn();

        assertNotNull("Should have header" + MllpConstants.MLLP_LOCAL_ADDRESS, message.getHeader(MllpConstants.MLLP_LOCAL_ADDRESS));
        assertNotNull("Should have header" + MllpConstants.MLLP_REMOTE_ADDRESS, message.getHeader(MllpConstants.MLLP_REMOTE_ADDRESS));

        assertNull("Should NOT have header" + MllpConstants.MLLP_SENDING_APPLICATION, message.getHeader(MllpConstants.MLLP_SENDING_APPLICATION));
        assertNull("Should NOT have header" + MllpConstants.MLLP_SENDING_FACILITY, message.getHeader(MllpConstants.MLLP_SENDING_FACILITY));
        assertNull("Should NOT have header" + MllpConstants.MLLP_RECEIVING_APPLICATION, message.getHeader(MllpConstants.MLLP_RECEIVING_APPLICATION));
        assertNull("Should NOT have header" + MllpConstants.MLLP_TIMESTAMP, message.getHeader(MllpConstants.MLLP_TIMESTAMP));
        assertNull("Should NOT have header" + MllpConstants.MLLP_SECURITY, message.getHeader(MllpConstants.MLLP_SECURITY));
        assertNull("Should NOT have header" + MllpConstants.MLLP_MESSAGE_TYPE, message.getHeader(MllpConstants.MLLP_MESSAGE_TYPE));
        assertNull("Should NOT have header" + MllpConstants.MLLP_EVENT_TYPE, message.getHeader(MllpConstants.MLLP_EVENT_TYPE));
        assertNull("Should NOT have header" + MllpConstants.MLLP_MESSAGE_CONTROL, message.getHeader(MllpConstants.MLLP_MESSAGE_CONTROL));
        assertNull("Should NOT have header" + MllpConstants.MLLP_PROCESSING_ID, message.getHeader(MllpConstants.MLLP_PROCESSING_ID));
        assertNull("Should NOT have header" + MllpConstants.MLLP_VERSION_ID, message.getHeader(MllpConstants.MLLP_VERSION_ID));
    }

    void addTestRoute(final boolean hl7Headers) throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            int connectTimeout = 500;
            int responseTimeout = 5000;

            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                onCompletion()
                    .to("mock://on-completion-result")
                    .toF("log:%s?level=INFO&showAll=true", routeId)
                    .log(LoggingLevel.INFO, routeId, "Test route complete");

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d&hl7Headers=%b",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), connectTimeout, responseTimeout, hl7Headers)
                    .routeId(routeId)
                    .log(LoggingLevel.INFO, routeId, "Test route received message")
                    .to(result);

            }
        };
        context.addRoutes(builder);
        context.start();
    }

}

