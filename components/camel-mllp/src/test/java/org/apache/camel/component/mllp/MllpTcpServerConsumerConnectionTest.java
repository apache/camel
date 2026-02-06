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

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit.rule.mllp.MllpJUnitResourceException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit6.ThrottlingExecutor.slowly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MllpTcpServerConsumerConnectionTest extends CamelTestSupport {
    static final int RECEIVE_TIMEOUT = 1000;
    static final int READ_TIMEOUT = 500;

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void doPreSetup() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());
    }

    /**
     * Simulate a Load Balancer Probe
     * <p/>
     * Load Balancers check the status of a port by establishing and closing a TCP connection periodically. The time
     * between these probes can normally be configured, but it is typically set to about 15-sec. Since there could be a
     * large number of port that are being probed, the logging from the connect/disconnect operations can drown-out more
     * useful information.
     * <p/>
     * Watch the logs when running this test to verify that the log output will be acceptable when a load balancer is
     * probing the port.
     * <p/>
     * TODO: Need to add a custom Log4j Appender that can verify the logging is acceptable
     *
     * @throws Exception
     */
    @Test
    public void testConnectThenCloseWithoutData() throws Exception {
        int connectionCount = 10;
        long connectionMillis = 200;

        result.setExpectedCount(0);
        result.setAssertPeriod(1000);

        addTestRouteWithIdleTimeout(-1);

        slowly().repeat(connectionCount).awaiting(connectionMillis, TimeUnit.MILLISECONDS)
                .beforeEach((i) -> mllpClient.connect())
                .afterEach((i) -> mllpClient.reset())
                .execute();

        // Connect one more time and allow a client thread to start
        mllpClient.connect();
        mllpClient.close();

        MockEndpoint.assertIsSatisfied(context, 15, TimeUnit.SECONDS);
    }

    @Test
    public void testConnectThenResetWithoutData() throws Exception {
        int connectionCount = 10;
        long connectionMillis = 200;

        result.setExpectedCount(0);
        result.setAssertPeriod(1000);

        addTestRouteWithIdleTimeout(-1);

        slowly().repeat(connectionCount).awaiting(connectionMillis, TimeUnit.MILLISECONDS)
                .beforeEach((i) -> mllpClient.connect())
                .afterEach((i) -> mllpClient.reset())
                .execute();

        // Connect one more time and allow a client thread to start
        mllpClient.connect();
        mllpClient.reset();

        MockEndpoint.assertIsSatisfied(context, 15, TimeUnit.SECONDS);
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

        addTestRouteWithIdleTimeout(idleTimeout);

        mllpClient.connect();
        mllpClient.sendMessageAndWaitForAcknowledgement(testMessage);

        Awaitility.await().untilAsserted(() -> {

            MllpJUnitResourceException ex = assertThrows(MllpJUnitResourceException.class, () -> mllpClient.checkConnection(),
                    "The MllpClientResource should have thrown an exception when writing to the reset socket");

            assertEquals("checkConnection failed - read() returned END_OF_STREAM", ex.getMessage());
            assertNull(ex.getCause());

            MockEndpoint.assertIsSatisfied(context, 15, TimeUnit.SECONDS);
        });
    }

    void addTestRouteWithIdleTimeout(final int idleTimeout) throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            String routeId = "mllp-receiver-with-timeout";

            public void configure() {
                fromF("mllp://%s:%d?receiveTimeout=%d&readTimeout=%d&idleTimeout=%d", mllpClient.getMllpHost(),
                        mllpClient.getMllpPort(), RECEIVE_TIMEOUT, READ_TIMEOUT, idleTimeout)
                        .routeId(routeId)
                        .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                        .to(result);
            }
        };

        context.addRoutes(builder);
        context.start();
    }

}
