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

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.apache.camel.test.AvailablePortFinder;

import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;

import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.instanceOf;

public class MllpTcpServerConsumerConnectionTest extends CamelTestSupport {
    static final int RECEIVE_TIMEOUT = 500;

    @Rule
    public MllpClientResource mllpClient = new MllpClientResource();


    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String routeId = "mllp-receiver";

            public void configure() {
                fromF("mllp://%s:%d?autoAck=false", mllpClient.getMllpHost(), mllpClient.getMllpPort())
                    .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                    .to(result);
            }
        };

    }

    /**
     * Simulate a Load Balancer Probe
     * <p/>
     * Load Balancers check the status of a port by establishing and closing a TCP connection periodically.  The time between these probes can normally be configured, but it is typically set to about
     * 15-sec.  Since there could be a large number of port that are being probed, the logging from the connect/disconnect operations can drown-out more useful information.
     * <p/>
     * Watch the logs when running this test to verify that the log output will be acceptable when a load balancer is probing the port.
     * <p/>
     * TODO:  Need to add a custom Log4j Appender that can verify the logging is acceptable
     *
     * @throws Exception
     */
    @Test
    public void testConnectThenCloseWithoutData() throws Exception {
        int connectionCount = 10;
        long connectionMillis = 200;

        result.setExpectedCount(0);
        result.setAssertPeriod(1000);

        addTestRoute(-1);

        for (int i = 1; i <= connectionCount; ++i) {
            mllpClient.connect();
            Thread.sleep(connectionMillis);
            mllpClient.close();
        }

        // Connect one more time and allow a client thread to start
        mllpClient.connect();
        Thread.sleep(1000);
        mllpClient.close();

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    @Test
    public void testConnectThenResetWithoutData() throws Exception {
        int connectionCount = 10;
        long connectionMillis = 200;

        result.setExpectedCount(0);
        result.setAssertPeriod(1000);

        addTestRoute(-1);

        for (int i = 1; i <= connectionCount; ++i) {
            mllpClient.connect();
            Thread.sleep(connectionMillis);
            mllpClient.reset();
        }

        // Connect one more time and allow a client thread to start
        mllpClient.connect();
        Thread.sleep(1000);
        mllpClient.reset();

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    /**
     * Simulate an Idle Client
     *
     * @throws Exception
     */
    @Test
    public void testIdleConnection() throws Exception {
        final int idleTimeout = RECEIVE_TIMEOUT * 3;
        String testMessage = "MSH|^~\\&|ADT|EPIC|JCAPS|CC|20160902123950|RISTECH|ADT^A08|00001|D|2.3|||||||" + '\r' + '\n';

        result.setExpectedCount(1);
        result.setAssertPeriod(1000);

        addTestRoute(idleTimeout);

        mllpClient.connect();
        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage);
        Thread.sleep(idleTimeout + RECEIVE_TIMEOUT);

        try {
            mllpClient.checkConnection();
            fail("The MllpClientResource should have thrown an exception when writing to the reset socket");
        } catch (MllpJUnitResourceException expectedEx) {
            assertEquals("checkConnection failed - read() returned END_OF_STREAM", expectedEx.getMessage());
            assertNull(expectedEx.getCause());
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }

    void addTestRoute(final int idleTimeout) throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            String routeId = "mllp-receiver";

            public void configure() {
                fromF("mllp://%s:%d?receiveTimeout=%d&idleTimeout=%d", mllpClient.getMllpHost(), mllpClient.getMllpPort(), RECEIVE_TIMEOUT, idleTimeout)
                    .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                    .to(result);
            }
        };

        context.addRoutes(builder);
        context.start();
    }

}
