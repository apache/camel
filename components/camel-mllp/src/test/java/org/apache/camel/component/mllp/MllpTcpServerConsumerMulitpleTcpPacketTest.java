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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.mllp.PassthroughProcessor;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.camel.test.mllp.Hl7MessageGenerator.generateMessage;

public class MllpTcpServerConsumerMulitpleTcpPacketTest extends CamelTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.setName(this.getClass().getSimpleName());

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
            public void configure() throws Exception {

                onCompletion()
                        .log(LoggingLevel.DEBUG, routeId, "Test route complete");

                fromF("mllp://%s:%d",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort())
                        .routeId(routeId)
                        .process(new PassthroughProcessor("Before send to result"))
                        .to(result)
                        .toF("log://%s?level=INFO&groupInterval=%d&groupActiveOnly=%b", routeId, groupInterval, groupActiveOnly)
                        .log(LoggingLevel.DEBUG, routeId, "Test route received message");

            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        mllpClient.connect();

        String message = generateMessage();
        result.expectedBodiesReceived(message);

        mllpClient.sendFramedDataInMultiplePackets(message, (byte) '\r');
        String acknowledgement = mllpClient.receiveFramedData();

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        Assert.assertThat("Should be acknowledgment for message 1", acknowledgement, CoreMatchers.containsString(String.format("MSA|AA|00001")));
    }


    @Test
    public void testReceiveMultipleMessages() throws Exception {
        int sendMessageCount = 1000;
        result.expectedMinimumMessageCount(5);

        mllpClient.connect();

        for (int i = 1; i <= sendMessageCount; ++i) {
            String testMessage = generateMessage(i);
            result.message(i - 1).body().isEqualTo(testMessage);
            mllpClient.sendFramedDataInMultiplePackets(testMessage, (byte) '\r');
            String acknowledgement = mllpClient.receiveFramedData();
            Assert.assertThat("Should be acknowledgment for message " + i, acknowledgement, CoreMatchers.containsString(String.format("MSA|AA|%05d", i)));
        }

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }


}
