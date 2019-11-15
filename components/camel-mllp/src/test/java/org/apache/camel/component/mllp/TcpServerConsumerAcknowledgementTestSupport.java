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
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;

public abstract class TcpServerConsumerAcknowledgementTestSupport extends CamelTestSupport {
    static final String TEST_MESSAGE =
        "MSH|^~\\&|APP_A|FAC_A|^org^sys||||ADT^A04^ADT_A04|||2.6" + '\r'
        + "PID|1||1100832^^^^PI||TEST^FIG||98765432|U||R|435 MAIN STREET^^LONGMONT^CO^80503||123-456-7890|||S" + '\r';

    static final String EXPECTED_ACKNOWLEDGEMENT =
        "MSH|^~\\&|^org^sys||APP_A|FAC_A|||ACK^A04^ADT_A04|||2.6" + '\r'
        + "MSA|AA|" + '\r';

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @EndpointInject("mock://on-complete-only")
    MockEndpoint complete;

    @EndpointInject("mock://on-failure-only")
    MockEndpoint failure;

    @EndpointInject("mock://invalid-ack-ex")
    MockEndpoint invalidAckEx;


    @EndpointInject("mock://ack-generation-ex")
    MockEndpoint ackGenerationEx;

    @Override
    protected void doPostSetup() throws Exception {
        super.doPostSetup();

        result.expectedMessageCount(0);
        complete.expectedMessageCount(0);
        failure.expectedMessageCount(0);
        invalidAckEx.expectedMessageCount(0);
        ackGenerationEx.expectedMessageCount(0);
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
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            int connectTimeout = 500;
            int responseTimeout = 5000;

            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                onException(MllpInvalidAcknowledgementException.class)
                    .handled(false)
                    .to("mock://invalid-ack-ex");

                onException(MllpAcknowledgementGenerationException.class)
                    .handled(false)
                    .to("mock://ack-generation-ex");

                onCompletion()
                    .onCompleteOnly()
                    .log(LoggingLevel.INFO, routeId, "Test route complete")
                    .to("mock://on-complete-only");

                onCompletion()
                    .onFailureOnly()
                    .log(LoggingLevel.INFO, routeId, "Test route complete")
                    .to("mock://on-failure-only");

                fromF("mllp://%s:%d?bridgeErrorHandler=%b&autoAck=%b&connectTimeout=%d&receiveTimeout=%d",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), isBridgeErrorHandler(), isAutoAck(), connectTimeout, responseTimeout)
                    .routeId(routeId)
                    .to(result);
            }
        };
    }

    protected abstract boolean isBridgeErrorHandler();
    protected abstract boolean isAutoAck();

    public void receiveSingleMessage() throws Exception {
        NotifyBuilder done = new NotifyBuilder(context).whenDone(1).create();

        mllpClient.connect();

        mllpClient.sendFramedData(TEST_MESSAGE);

        assertTrue("Exchange should have completed", done.matches(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied();
    }

    public void acknowledgementDeliveryFailure() throws Exception {
        boolean disconnectAfterSend = true;
        mllpClient.setDisconnectMethod(MllpClientResource.DisconnectMethod.RESET);
        mllpClient.connect();

        mllpClient.sendFramedData(TEST_MESSAGE, disconnectAfterSend);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    public void unparsableMessage(String testMessage) throws Exception {
        NotifyBuilder done = new NotifyBuilder(context).whenDone(1).create();

        mllpClient.connect();
        mllpClient.sendFramedData(testMessage);

        assertTrue("One exchange should have complete", done.matches(5, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();
    }
}

