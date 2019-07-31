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
package org.apache.camel;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceTimeoutException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.Rule;
import org.junit.Test;

public class MllpTcpServerConsumerLenientBindTest extends CamelTestSupport {
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    ServerSocket portBlocker;

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        portBlocker = new ServerSocket(mllpClient.getMllpPort());

        assertTrue(portBlocker.isBound());

        super.doPreSetup();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            String routeId = "mllp-receiver-with-lenient-bind";

            public void configure() {
                fromF("mllp://%s:%d?bindTimeout=15000&bindRetryInterval=500&receiveTimeout=%d&readTimeout=%d&reuseAddress=false&lenientBind=true",
                    mllpClient.getMllpHost(), mllpClient.getMllpPort(), RECEIVE_TIMEOUT, READ_TIMEOUT)
                    .routeId(routeId)
                    .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                    .to(result);
            }
        };

        return builder;
    }

    @Test
    public void testLenientBind() throws Exception {
        assertEquals(ServiceStatus.Started, context.getStatus());

        mllpClient.connect();
        try {
            mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10001));
        } catch (MllpJUnitResourceTimeoutException expectedEx) {
            assertIsInstanceOf(SocketTimeoutException.class, expectedEx.getCause());
        }
        mllpClient.reset();

        portBlocker.close();
        Thread.sleep(2000);
        assertEquals(ServiceStatus.Started, context.getStatus());

        mllpClient.connect();
        String acknowledgement = mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage(10002));
        assertStringContains(acknowledgement, "10002");
    }



}
