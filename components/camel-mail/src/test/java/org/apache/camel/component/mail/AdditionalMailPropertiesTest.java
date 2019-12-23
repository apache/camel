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

import java.util.Properties;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test allowing end users to set additional mail.xxx properties.
 */
public class AdditionalMailPropertiesTest extends CamelTestSupport {

    @Test
    public void testAdditionalMailProperties() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        MailEndpoint endpoint = context.getEndpoint("pop3://localhost?username=james&mail.pop3.forgettopheaders=true&initialDelay=100&delay=100", MailEndpoint.class);
        Properties prop = endpoint.getConfiguration().getAdditionalJavaMailProperties();
        assertEquals("true", prop.get("mail.pop3.forgettopheaders"));
    }

    @Test
    public void testConsumeWithAdditionalProperties() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        MockEndpoint mock = getMockEndpoint("mock:result");

        template.sendBodyAndHeader("smtp://james@localhost", "Hello james how are you?", "subject", "Hello");

        mock.expectedBodiesReceived("Hello james how are you?");
        mock.expectedHeaderReceived("subject", "Hello");
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("pop3://james@localhost?mail.pop3.forgettopheaders=true&initialDelay=100&delay=100").to("mock:result");
            }
        };

    }
}