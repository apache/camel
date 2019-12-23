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
package org.apache.camel.component.nagios;

import java.util.HashMap;
import java.util.Map;

import com.googlecode.jsendnsca.Level;
import com.googlecode.jsendnsca.MessagePayload;
import com.googlecode.jsendnsca.NagiosPassiveCheckSender;
import com.googlecode.jsendnsca.PassiveCheckSender;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NagiosTest extends CamelTestSupport {
    @Mock @BindToRegistry("mySender")
    protected static PassiveCheckSender nagiosPassiveCheckSender;

    protected boolean canRun;

    @BeforeClass
    public static void setSender() {
        nagiosPassiveCheckSender =  Mockito.mock(NagiosPassiveCheckSender.class);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        canRun = true;
        super.setUp();
    }

    @Test
    public void testSendToNagios() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload = new MessagePayload("localhost", Level.OK, context.getName(),  "Hello Nagios");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.allMessages().body().isInstanceOf(String.class);
        mock.expectedBodiesReceived("Hello Nagios");

        template.sendBody("direct:start", "Hello Nagios");

        assertMockEndpointsSatisfied();

        verify(nagiosPassiveCheckSender, times(1)).send(expectedPayload);
    }

    @Test
    public void testSendTwoToNagios() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload1 = new MessagePayload("localhost", Level.OK, context.getName(),  "Hello Nagios");
        MessagePayload expectedPayload2 = new MessagePayload("localhost", Level.OK, context.getName(),  "Bye Nagios");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.allMessages().body().isInstanceOf(String.class);
        mock.expectedBodiesReceived("Hello Nagios", "Bye Nagios");

        template.sendBody("direct:start", "Hello Nagios");
        template.sendBody("direct:start", "Bye Nagios");

        assertMockEndpointsSatisfied();

        verify(nagiosPassiveCheckSender).send(expectedPayload1);
        verify(nagiosPassiveCheckSender).send(expectedPayload2);
    }

    @Test
    public void testSendToNagiosWarn() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload1 = new MessagePayload("localhost", Level.WARNING, context.getName(),  "Hello Nagios");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Nagios");

        template.sendBodyAndHeader("direct:start", "Hello Nagios", NagiosConstants.LEVEL, Level.WARNING);

        assertMockEndpointsSatisfied();
        verify(nagiosPassiveCheckSender).send(expectedPayload1);
    }

    @Test
    public void testSendToNagiosWarnAsText() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload1 = new MessagePayload("localhost", Level.WARNING, context.getName(),  "Hello Nagios");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Nagios");

        template.sendBodyAndHeader("direct:start", "Hello Nagios", NagiosConstants.LEVEL, "WARNING");

        assertMockEndpointsSatisfied();

        verify(nagiosPassiveCheckSender).send(expectedPayload1);
    }

    @Test
    public void testSendToNagiosMultiHeaders() throws Exception {
        if (!canRun) {
            return;
        }

        MessagePayload expectedPayload1 = new MessagePayload("myHost", Level.CRITICAL, "myService",  "Hello Nagios");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Nagios");

        Map<String, Object> headers = new HashMap<>();
        headers.put(NagiosConstants.LEVEL, "CRITICAL");
        headers.put(NagiosConstants.HOST_NAME, "myHost");
        headers.put(NagiosConstants.SERVICE_NAME, "myService");
        template.sendBodyAndHeaders("direct:start", "Hello Nagios", headers);

        assertMockEndpointsSatisfied();
        verify(nagiosPassiveCheckSender).send(expectedPayload1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("nagios:127.0.0.1:25664?password=secret&sender=#mySender")
                        .to("mock:result");
            }
        };
    }

}
