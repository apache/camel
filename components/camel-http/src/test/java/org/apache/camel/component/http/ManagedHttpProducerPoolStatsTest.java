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
package org.apache.camel.component.http;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ManagedHttpProducerPoolStatsTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/myapp", (request, response, context) -> {
                    response.setEntity(new StringEntity("OK", "ASCII"));
                    response.setStatusCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testPoolStats() throws Exception {
        // turn on registering jmx always so the producer is also registered
        context.getManagementStrategy().getManagementAgent().setRegisterAlways(true);

        String uri = "http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/myapp";

        Exchange out = template.request(uri, exchange -> exchange.getIn().setBody("Hello World"));

        assertNotNull(out);
        assertEquals("OK", out.getMessage().getBody(String.class));

        // look up stats
        HttpEndpoint http = context.getEndpoint(uri, HttpEndpoint.class);
        assertNotNull(http);

        int max = http.getClientConnectionsPoolStatsMax();
        int avail = http.getClientConnectionsPoolStatsAvailable();
        int leased = http.getClientConnectionsPoolStatsLeased();
        int pending = http.getClientConnectionsPoolStatsPending();

        assertEquals(200, max);
        assertEquals(1, avail);
        assertEquals(0, leased);
        assertEquals(0, pending);

        // should be in JMX too
        MBeanServer mbeanServer = getMBeanServer();
        String id = context.getManagementName();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + id + ",type=endpoints,name=\"" + uri + "\"");
        assertTrue(mbeanServer.isRegistered(on));

        max = (int) mbeanServer.getAttribute(on, "ClientConnectionsPoolStatsMax");
        assertEquals(200, max);
        avail = (int) mbeanServer.getAttribute(on, "ClientConnectionsPoolStatsAvailable");
        assertEquals(1, avail);
        leased = (int) mbeanServer.getAttribute(on, "ClientConnectionsPoolStatsLeased");
        assertEquals(0, leased);
        pending = (int) mbeanServer.getAttribute(on, "ClientConnectionsPoolStatsPending");
        assertEquals(0, pending);
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }


}
