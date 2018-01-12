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
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpServerConsumerAcknowledgementTest extends CamelTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

    @EndpointInject(uri = "mock://on-complete-only")
    MockEndpoint complete;

    @EndpointInject(uri = "mock://on-failure-only")
    MockEndpoint failure;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            int connectTimeout = 500;
            int responseTimeout = 5000;

            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                onCompletion()
                    .onCompleteOnly()
                    .log(LoggingLevel.INFO, routeId, "Test route complete")
                    .to("mock://on-complete-only");
                onCompletion()
                    .onFailureOnly()
                    .log(LoggingLevel.INFO, routeId, "Test route complete")
                    .to("mock://on-failure-only");
                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), connectTimeout, responseTimeout)
                    .routeId(routeId)
                    .to(result);
            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        final String testMessage =
            "MSH|^~\\&|APP_A|FAC_A|^org^sys||||ADT^A04^ADT_A04|||2.6" + '\r'
                + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r';

        final String expectedAcknowledgement =
            "MSH|^~\\&|^org^sys||APP_A|FAC_A|||ACK^A04^ADT_A04|||2.6" + '\r'
                + "MSA|AA|" + '\r';

        result.expectedBodiesReceived(testMessage);

        complete.expectedBodiesReceived(testMessage);
        complete.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        complete.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, expectedAcknowledgement);
        complete.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, expectedAcknowledgement);

        failure.expectedMessageCount(0);

        mllpClient.connect();

        String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(testMessage, 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        assertEquals("Unexpected Acknowledgement", expectedAcknowledgement, acknowledgement);
    }

    @Test
    public void testAcknowledgementDeliveryFailure() throws Exception {
        final String testMessage =
            "MSH|^~\\&|APP_A|FAC_A|^org^sys||||ADT^A04^ADT_A04|||2.6" + '\r'
                + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r';

        final String expectedAcknowledgement =
            "MSH|^~\\&|^org^sys||APP_A|FAC_A|||ACK^A04^ADT_A04|||2.6" + '\r'
                + "MSA|AA|" + '\r';

        result.expectedBodiesReceived(testMessage);

        complete.expectedMessageCount(0);

        failure.expectedBodiesReceived(testMessage);
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_TYPE, "AA");
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT, expectedAcknowledgement);
        failure.expectedHeaderReceived(MllpConstants.MLLP_ACKNOWLEDGEMENT_STRING, expectedAcknowledgement);

        boolean disconnectAfterSend = true;
        mllpClient.setDisconnectMethod(MllpClientResource.DisconnectMethod.RESET);
        mllpClient.connect();

        mllpClient.sendFramedData(testMessage, disconnectAfterSend);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        Exchange failureExchange = failure.getExchanges().get(0);
        Object failureException = failureExchange.getProperty(MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION);
        assertNotNull("OnFailureOnly exchange should have a " + MllpConstants.MLLP_ACKNOWLEDGEMENT_EXCEPTION + " property", failureException);
        assertIsInstanceOf(Exception.class, failureException);
    }
}

