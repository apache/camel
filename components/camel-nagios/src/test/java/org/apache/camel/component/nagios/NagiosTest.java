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
package org.apache.camel.component.nagios;

import java.util.HashMap;
import java.util.Map;

import com.googlecode.jsendnsca.core.Level;
import com.googlecode.jsendnsca.core.MessagePayload;
import com.googlecode.jsendnsca.core.mocks.NagiosNscaStub;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class NagiosTest extends CamelTestSupport {
    protected boolean canRun;
    private NagiosNscaStub nagios;
    
    @Before
    @Override
    public void setUp() throws Exception {
        canRun = true;

        nagios = new NagiosNscaStub(25667, "secret");
        try {
            nagios.start();
        } catch (Exception e) {
            log.warn("Error starting NagiosNscaStub. This exception is ignored.", e);
            canRun = false;
        }

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        try {
            nagios.stop();
        } catch (Exception e) {
            // ignore
            log.warn("Error stopping NagiosNscaStub. This exception is ignored.", e);
        }
    }

    @Test
    public void testSendToNagios() throws Exception {
        if (!canRun) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.allMessages().body().isInstanceOf(String.class);

        template.sendBody("direct:start", "Hello Nagios");

        assertMockEndpointsSatisfied();

        // sleep a little to let nagios stub process the payloads
        Thread.sleep(2000);

        assertEquals(1, nagios.getMessagePayloadList().size());

        MessagePayload payload = nagios.getMessagePayloadList().get(0);
        assertEquals("Hello Nagios", payload.getMessage());
        assertEquals("localhost", payload.getHostname());
        assertEquals(Level.OK.ordinal(), payload.getLevel());
        assertEquals(context.getName(), payload.getServiceName());
    }

    @Test
    public void testSendTwoToNagios() throws Exception {
        if (!canRun) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.allMessages().body().isInstanceOf(String.class);

        template.sendBody("direct:start", "Hello Nagios");
        template.sendBody("direct:start", "Bye Nagios");

        assertMockEndpointsSatisfied();

        // sleep a little to let nagios stub process the payloads
        Thread.sleep(3000);

        assertEquals(2, nagios.getMessagePayloadList().size());

        MessagePayload payload = nagios.getMessagePayloadList().get(0);
        MessagePayload payload2 = nagios.getMessagePayloadList().get(1);

        assertEquals("localhost", payload.getHostname());
        assertEquals(Level.OK.ordinal(), payload.getLevel());
        assertEquals(context.getName(), payload.getServiceName());
        assertEquals("localhost", payload2.getHostname());
        assertEquals(Level.OK.ordinal(), payload2.getLevel());
        assertEquals(context.getName(), payload2.getServiceName());

        // when using async they may arrive in different order
        boolean hello = "Hello Nagios".equals(payload.getMessage()) || "Hello Nagios".equals(payload2.getMessage());
        boolean bye = "Bye Nagios".equals(payload.getMessage()) || "Bye Nagios".equals(payload2.getMessage());
        assertTrue("Should have received Hello Nagios message", hello);
        assertTrue("Should have received Bye Nagios message", bye);
    }

    @Test
    public void testSendToNagiosWarn() throws Exception {
        if (!canRun) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello Nagios", NagiosConstants.LEVEL, Level.WARNING);

        assertMockEndpointsSatisfied();

        // sleep a little to let nagios stub process the payloads
        Thread.sleep(2000);

        assertEquals(1, nagios.getMessagePayloadList().size());

        MessagePayload payload = nagios.getMessagePayloadList().get(0);
        assertEquals("Hello Nagios", payload.getMessage());
        assertEquals("localhost", payload.getHostname());
        assertEquals(Level.WARNING.ordinal(), payload.getLevel());
        assertEquals(context.getName(), payload.getServiceName());
    }

    @Test
    public void testSendToNagiosWarnAsText() throws Exception {
        if (!canRun) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello Nagios", NagiosConstants.LEVEL, "WARNING");

        assertMockEndpointsSatisfied();

        // sleep a little to let nagios stub process the payloads
        Thread.sleep(2000);

        assertEquals(1, nagios.getMessagePayloadList().size());

        MessagePayload payload = nagios.getMessagePayloadList().get(0);
        assertEquals("Hello Nagios", payload.getMessage());
        assertEquals("localhost", payload.getHostname());
        assertEquals(Level.WARNING.ordinal(), payload.getLevel());
        assertEquals(context.getName(), payload.getServiceName());
    }

    @Test
    public void testSendToNagiosMultiHeaders() throws Exception {
        if (!canRun) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(NagiosConstants.LEVEL, "CRITICAL");
        headers.put(NagiosConstants.HOST_NAME, "myHost");
        headers.put(NagiosConstants.SERVICE_NAME, "myService");
        template.sendBodyAndHeaders("direct:start", "Hello Nagios", headers);

        assertMockEndpointsSatisfied();

        // sleep a little to let nagios stub process the payloads
        Thread.sleep(2000);

        assertEquals(1, nagios.getMessagePayloadList().size());

        MessagePayload payload = nagios.getMessagePayloadList().get(0);
        assertEquals("Hello Nagios", payload.getMessage());
        assertEquals("myHost", payload.getHostname());
        assertEquals(Level.CRITICAL.ordinal(), payload.getLevel());
        assertEquals("myService", payload.getServiceName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start").to("nagios:127.0.0.1:25667?password=secret").to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}