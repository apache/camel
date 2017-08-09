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

import com.googlecode.jsendnsca.MessagePayload;
import com.googlecode.jsendnsca.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.PassiveCheckSender;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;
/**
 * @version 
 */
public class NagiosEventNotifierTest extends CamelTestSupport {
    protected boolean canRun;

    @Mock
    private PassiveCheckSender nagiosPassiveCheckSender = Mockito.mock(NagiosPassiveCheckSender.class);


    @Override
    protected boolean useJmx() {
        return true;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        canRun = true;
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        NagiosEventNotifier notifier = new NagiosEventNotifier(nagiosPassiveCheckSender);
        notifier.getConfiguration().setHost("localhost");
        notifier.getConfiguration().setPort(25669);
        notifier.getConfiguration().setPassword("password");

        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testNagiosEventNotifierOk() throws Exception {
        if (!canRun) {
            return;
        }

        getMockEndpoint("mock:ok").expectedMessageCount(1);

        template.sendBody("direct:ok", "Hello World");

        assertMockEndpointsSatisfied();

        context.stop();

        verify(nagiosPassiveCheckSender, atLeast(11)).send(any(MessagePayload.class));
    }

    @Test
    public void testNagiosEventNotifierError() throws Exception {
        if (!canRun) {
            return;
        }

        try {
            template.sendBody("direct:fail", "Bye World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // ignore
        }

        context.stop();

        verify(nagiosPassiveCheckSender, atLeast(9)).send(any(MessagePayload.class));
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
