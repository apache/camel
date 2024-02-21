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

import jakarta.mail.Message;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MailToMultipleEndpointsTest extends CamelTestSupport {
    private static final MailboxUser james2 = Mailbox.getOrCreateUser("james2", "secret");
    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");
    private static final MailboxUser admin = Mailbox.getOrCreateUser("admin", "secret");

    private static final MailboxUser a = Mailbox.getOrCreateUser("a", "secret");
    private static final MailboxUser b = Mailbox.getOrCreateUser("b", "secret");
    private static final MailboxUser c = Mailbox.getOrCreateUser("c", "secret");

    @Test
    public void testMultipleEndpoints() throws Exception {
        Mailbox.clearAll();

        template.sendBodyAndHeader("direct:a", "Hello World", "Subject", "Hello a");
        template.sendBodyAndHeader("direct:b", "Bye World", "Subject", "Hello b");
        template.sendBodyAndHeader("direct:c", "Hi World", "Subject", "Hello c");

        Mailbox boxA = a.getInbox();
        assertEquals(1, boxA.getMessageCount());
        assertEquals("Hello a", boxA.get(0).getSubject());
        assertEquals("Hello World", boxA.get(0).getContent());
        assertEquals("me@me.com", boxA.get(0).getFrom()[0].toString());

        Mailbox boxB = b.getInbox();
        assertEquals(1, boxB.getMessageCount());
        assertEquals("Hello b", boxB.get(0).getSubject());
        assertEquals("Bye World", boxB.get(0).getContent());
        assertEquals("you@you.com", boxB.get(0).getFrom()[0].toString());

        Mailbox boxC = c.getInbox();
        assertEquals(1, boxC.getMessageCount());
        assertEquals("Hello c", boxC.get(0).getSubject());
        assertEquals("Hi World", boxC.get(0).getContent());
        assertEquals("me@me.com", boxC.get(0).getFrom()[0].toString());
        assertEquals("you@you.com", boxC.get(0).getRecipients(Message.RecipientType.CC)[0].toString());
        assertEquals("them@them.com", boxC.get(0).getRecipients(Message.RecipientType.CC)[1].toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").to(james2.uriPrefix(Protocol.smtp) + "&to=" + a.getEmail() + "&from=me@me.com");

                from("direct:b").to(james.uriPrefix(Protocol.smtp) + "&to=" + b.getEmail() + "&from=you@you.com");

                from("direct:c").to(
                        admin.uriPrefix(Protocol.smtp) + "&to=" + c.getEmail()
                                    + "&from=me@me.com&cc=you@you.com,them@them.com");
            }
        };
    }
}
