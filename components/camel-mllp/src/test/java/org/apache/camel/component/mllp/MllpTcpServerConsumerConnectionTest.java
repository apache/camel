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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MllpTcpServerConsumerConnectionTest extends CamelTestSupport {
    int mllpPort;
    @EndpointInject(uri = "mock://result")
    MockEndpoint result;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        mllpPort = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            String routeId = "mllp-receiver";

            String host = "0.0.0.0";
            int port = mllpPort;

            public void configure() {
                fromF("mllp:%d?autoAck=false", port)
                        .log(LoggingLevel.INFO, routeId, "Receiving: ${body}")
                        .to(result);
            }
        };

    }

    /**
     * Simulate a Load Balancer Probe
     * <p/>
     * Load Balancers check the status of a port by establishing and closing a TCP connection periodically.  The time
     * between these probes can normally be configured, but it is typically set to about 15-sec.  Since there could be
     * a large number of port that are being probed, the logging from the connect/disconnect operations can drown-out
     * more useful information.
     * <p/>
     * Watch the logs when running this test to verify that the log output will be acceptable when a load balancer
     * is probing the port.
     * <p/>
     * TODO:  Need to add a custom Log4j Appender that can verify the logging is acceptable
     *
     * @throws Exception
     */
    @Test
    public void testConnectWithoutData() throws Exception {
        result.setExpectedCount(0);
        int connectionCount = 10;

        Socket dummyLoadBalancerSocket = null;
        SocketAddress address = new InetSocketAddress("localhost", mllpPort);
        int connectTimeout = 5000;
        try {
            for (int i = 1; i <= connectionCount; ++i) {
                log.debug("Creating connection #{}", i);
                dummyLoadBalancerSocket = new Socket();
                dummyLoadBalancerSocket.connect(address, connectTimeout);
                log.debug("Closing connection #{}", i);
                dummyLoadBalancerSocket.close();
                Thread.sleep(1000);
            }
        } finally {
            if (null != dummyLoadBalancerSocket) {
                try {
                    dummyLoadBalancerSocket.close();
                } catch (Exception ex) {
                    log.warn("Exception encountered closing dummy load balancer socket", ex);
                }
            }
        }

        assertMockEndpointsSatisfied(15, TimeUnit.SECONDS);
    }


}
