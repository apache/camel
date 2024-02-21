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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Unit test for Mail using @ in username option
 */
public class MailUsernameWithAtSignTest extends CamelTestSupport {
    private static final MailboxUser jamesAtSign
            = Mailbox.getOrCreateUser("jamesAtSign@localhost", "jamesAtSign@localhost", "secret");

    @Test
    public void testMailUsingAtSignInUsername() throws Exception {
        Mailbox.clearAll();

        String body = "Hello Claus.\r\nYes it does.\r\n\r\nRegards James.\r\n";
        template.sendBody("direct:a", body);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(body);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to("smtp://localhost:" + Mailbox.getPort(Protocol.smtp)
                                    + "?username=jamesAtSign@localhost&password=secret");

                from("pop3://localhost:" + Mailbox.getPort(Protocol.pop3) + "?username=" + jamesAtSign.getEmail() + "&password="
                     + jamesAtSign.getPassword() + "&initialDelay=100&delay=100").to("mock:result");
            }
        };
    }
}
