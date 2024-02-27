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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.apache.camel.test.mllp.PassthroughProcessor;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.MatcherAssert.assertThat;

public class MllpTcpServerConsumerMulitpleTcpPacketTest extends CamelTestSupport {
    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final int groupInterval = 1000;
        final boolean groupActiveOnly = false;

        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            String routeId = "mllp-receiver";

            @Override
            public void configure() {

                onCompletion()
                        .log(LoggingLevel.INFO, routeId, "Test route complete");

                fromF("mllp://%s:%d",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort())
                        .routeId(routeId)
                        .process(new PassthroughProcessor("Before send to result"))
                        .to(result)
                        .toF("log://%s?level=INFO&groupInterval=%d&groupActiveOnly=%b", routeId, groupInterval,
                                groupActiveOnly)
                        .log(LoggingLevel.DEBUG, routeId, "Test route received message");

            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        mllpClient.connect();

        String message = Hl7TestMessageGenerator.generateMessage();
        result.expectedBodiesReceived(message);

        mllpClient.sendFramedDataInMultiplePackets(message, (byte) '\r');
        String acknowledgement = mllpClient.receiveFramedData();

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

        assertThat("Should be acknowledgment for message 1", acknowledgement,
                CoreMatchers.containsString("MSA|AA|00001"));
    }

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        int sendMessageCount = 100;
        result.expectedMessageCount(sendMessageCount);

        mllpClient.setSoTimeout(10000);
        mllpClient.connect();

        for (int i = 1; i <= sendMessageCount; ++i) {
            String testMessage = Hl7TestMessageGenerator.generateMessage(i);
            result.message(i - 1).body().isEqualTo(testMessage);
            mllpClient.sendFramedDataInMultiplePackets(testMessage, (byte) '\r');
            String acknowledgement = mllpClient.receiveFramedData();
            assertThat("Should be acknowledgment for message " + i, acknowledgement,
                    CoreMatchers.containsString(String.format("MSA|AA|%05d", i)));
        }

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

}
