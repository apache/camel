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
package org.apache.camel.component.mail;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;

import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class MailUsingCustomSessionTest extends CamelTestSupport {

    @BindToRegistry("myCustomMailSession")
    private Session mailSession = Session.getInstance(new Properties());

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Mailbox.clearAll();
    }

    @Test
    public void testEndpointConfigurationWithCustomSession() {
        // Verify that the mail session bound to the bean registry is identical
        // to the session tied to the endpoint configuration
        assertSame(mailSession, getEndpointMailSession("smtp://james@localhost?session=#myCustomMailSession"));
    }

    @Test
    public void testSendAndReceiveMailsWithCustomSession() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("hello camel!");

        Map<String, Object> headers = new HashMap<>();
        headers.put("subject", "Hello Camel");
        template.sendBodyAndHeaders("smtp://james@localhost?session=#myCustomMailSession", "hello camel!", headers);

        mockEndpoint.assertIsSatisfied();

        Mailbox mailbox = Mailbox.get("james@localhost");
        assertEquals("Expected one mail for james@localhost", 1, mailbox.size());

        Message message = mailbox.get(0);
        assertEquals("hello camel!", message.getContent());
        assertEquals("camel@localhost", message.getFrom()[0].toString());
    }

    private Session getEndpointMailSession(String uri) {
        MailEndpoint endpoint = context.getEndpoint(uri, MailEndpoint.class);
        return endpoint.getConfiguration().getSession();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("pop3://james@localhost?session=#myCustomMailSession&initialDelay=100&delay=100").to("mock:result");
            }
        };
    }
}
