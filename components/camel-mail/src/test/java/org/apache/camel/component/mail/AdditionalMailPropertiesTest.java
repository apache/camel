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
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test allowing end users to set additional mail.xxx properties.
 */
public class AdditionalMailPropertiesTest extends CamelTestSupport {
    private static final MailboxUser user = Mailbox.getOrCreateUser("additionalMailProperties");

    @Test
    public void testAdditionalMailProperties() {
        // clear mailbox
        Mailbox.clearAll();

        MailEndpoint endpoint = context.getEndpoint(
                user.uriPrefix(Protocol.pop3) + "&mail.pop3.forgettopheaders=true&initialDelay=100&delay=100",
                MailEndpoint.class);
        Properties prop = endpoint.getConfiguration().getAdditionalJavaMailProperties();
        assertEquals("true", prop.get("mail.pop3.forgettopheaders"));
    }

    @Test
    public void testConsumeWithAdditionalProperties() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        MockEndpoint mock = getMockEndpoint("mock:result");

        template.sendBodyAndHeader(user.uriPrefix(Protocol.smtp), "Hello james how are you?\r\n", "subject", "Hello");

        mock.expectedBodiesReceived("Hello james how are you?\r\n");
        mock.expectedHeaderReceived("subject", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(user.uriPrefix(Protocol.pop3) + "&mail.pop3.forgettopheaders=true&initialDelay=100&delay=100")
                        .to("mock:result");
            }
        };

    }
}
