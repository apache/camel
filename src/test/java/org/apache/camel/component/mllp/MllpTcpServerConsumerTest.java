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
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceTimeoutException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.apache.camel.test.Data.*;

public class MllpTcpServerConsumerTest extends CamelTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject(uri = "mock://request")
    MockEndpoint request;

    @Override
    public String isMockEndpoints() {
        return "log:*";
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

        mllpClient.setMllpHost( "localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            int connectTimeout = 500;
            int responseTimeout = 5000;

            @Override
            public void configure() throws Exception {
                String routeId = "mllp-test-receiver-route";

                onCompletion()
                        .toF("log:%s?level=INFO&showAll=true", routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route complete")
                ;

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&responseTimeout=%d",
                        mllpClient.getMllpHost(), mllpClient.getMllpPort(), connectTimeout, responseTimeout)
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Test route received message")
                        .to("mock://request")
                ;

            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        request.expectedMinimumMessageCount(1);

        mllpClient.connect();

        mllpClient.sendFramedData(TEST_MESSAGE_1);
        mllpClient.receiveFramedData( 10000 );

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReceiveSingleMessageWithDelayAfterConnection() throws Exception {
        request.expectedMinimumMessageCount(1);

        mllpClient.connect();

        Thread.sleep(5000);
        mllpClient.sendFramedData(TEST_MESSAGE_1);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReceiveMultipleMessages() throws Exception {
        request.expectedMinimumMessageCount(5);

        mllpClient.connect();

        mllpClient.sendFramedData(TEST_MESSAGE_1);
        mllpClient.sendFramedData(TEST_MESSAGE_2);
        mllpClient.sendFramedData(TEST_MESSAGE_3);
        mllpClient.sendFramedData(TEST_MESSAGE_4);
        mllpClient.sendFramedData(TEST_MESSAGE_5);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testOpenMllpEnvelopeWithReset() throws Exception {
        request.expectedMessageCount(4);
        NotifyBuilder notify1 = new NotifyBuilder(context).whenDone(2).create();
        NotifyBuilder notify2 = new NotifyBuilder(context).whenDone(5).create();

        mllpClient.connect();
        mllpClient.setSoTimeout(10000);

        log.info("Sending TEST_MESSAGE_1");
        mllpClient.sendFramedData(TEST_MESSAGE_1);
        String acknowledgement1 = mllpClient.receiveFramedData();

        log.info("Sending TEST_MESSAGE_2");
        mllpClient.sendFramedData(TEST_MESSAGE_2);
        String acknowledgement2 = mllpClient.receiveFramedData();

        assertTrue("First two normal exchanges did not complete", notify1.matches(10, TimeUnit.SECONDS));

        log.info("Sending TEST_MESSAGE_3");
        mllpClient.setSendEndOfBlock(false);
        mllpClient.setSendEndOfData(false);
        mllpClient.sendFramedData(TEST_MESSAGE_3);
        // Acknowledgement won't come here
        try {
            mllpClient.receiveFramedData();
        } catch (MllpJUnitResourceTimeoutException timeoutEx) {
            log.info("Expected Timeout reading response");
        }
        mllpClient.disconnect();
        Thread.sleep(1000);
        mllpClient.connect();

        log.info("Sending TEST_MESSAGE_4");
        mllpClient.setSendEndOfBlock(true);
        mllpClient.setSendEndOfData(true);
        mllpClient.sendFramedData(TEST_MESSAGE_4);
        String acknowledgement4 = mllpClient.receiveFramedData();

        log.info("Sending TEST_MESSAGE_5");
        mllpClient.sendFramedData(TEST_MESSAGE_5);
        String acknowledgement5 = mllpClient.receiveFramedData();

        assertTrue("Remaining exchanges did not complete", notify2.matches(10, TimeUnit.SECONDS));

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);

        assertTrue("Should be acknowledgment for message 1", acknowledgement1.contains("MSA|AA|10001"));
        assertTrue("Should be acknowledgment for message 2", acknowledgement2.contains("MSA|AA|10002"));
        // assertTrue("Should be acknowledgment for message 3", acknowledgement3.contains("MSA|AA|10003"));
        assertTrue("Should be acknowledgment for message 4", acknowledgement4.contains("MSA|AA|10004"));
        assertTrue("Should be acknowledgment for message 5", acknowledgement5.contains("MSA|AA|10005"));
    }

}

