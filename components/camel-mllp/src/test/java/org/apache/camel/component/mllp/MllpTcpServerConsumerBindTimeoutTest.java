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

import java.net.ServerSocket;
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
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpServerConsumerBindTimeoutTest extends CamelTestSupport {
    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @Override
    public boolean isUseAdviceWith() {
        return true;
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

                onCompletion()
                    .toF("log:%s?level=INFO&showAll=true", routeId)
                    .log(LoggingLevel.INFO, routeId, "Test route complete");

                fromF("mllp://%s:%d?autoAck=true&connectTimeout=%d&receiveTimeout=%d",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), connectTimeout, responseTimeout)
                    .routeId(routeId)
                    .log(LoggingLevel.INFO, routeId, "Test route received message")
                    .to(result);

            }
        };
    }

    @Test
    public void testReceiveSingleMessage() throws Exception {
        result.expectedMessageCount(1);

        Thread tmpThread = new Thread() {
            public void run() {
                try {
                    ServerSocket tmpSocket = new ServerSocket(mllpClient.getMllpPort());
                    Thread.sleep(15000);
                    tmpSocket.close();
                } catch (Exception ex) {
                    throw new RuntimeException("Exception caught in dummy listener", ex);
                }
            }

        };

        tmpThread.start();

        context.start();

        mllpClient.connect();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(), 10000);

        assertMockEndpointsSatisfied(10, TimeUnit.SECONDS);
    }

}

