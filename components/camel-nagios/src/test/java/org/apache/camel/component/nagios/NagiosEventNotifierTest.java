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

import java.util.List;

import com.googlecode.jsendnsca.core.MessagePayload;
import com.googlecode.jsendnsca.core.mocks.NagiosNscaStub;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class NagiosEventNotifierTest extends CamelTestSupport {

    private NagiosNscaStub nagios;

    @Before
    @Override
    public void setUp() throws Exception {
        nagios = new NagiosNscaStub(5667, "password");
        nagios.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        nagios.stop();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        NagiosEventNotifier notifier = new NagiosEventNotifier();
        notifier.getConfiguration().setHost("localhost");
        notifier.getConfiguration().setPort(5667);
        notifier.getConfiguration().setPassword("password");

        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testNagiosEventNotifierOk() throws Exception {
        getMockEndpoint("mock:ok").expectedMessageCount(1);

        template.sendBody("direct:ok", "Hello World");

        assertMockEndpointsSatisfied();

        context.stop();

        List<MessagePayload> events = nagios.getMessagePayloadList();
        assertEquals(10, events.size());
    }

    @Test
    public void testNagiosEventNotifierError() throws Exception {
        try {
            template.sendBody("direct:fail", "Bye World");
        } catch (Exception e) {
            // ignore
        }

        context.stop();

        List<MessagePayload> events = nagios.getMessagePayloadList();
        assertEquals(10, events.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:ok").to("mock:ok");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
