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

/**
 * Unit test for recipients using | in email address
 */
public class MailRecipientsPipeIssueTest extends CamelTestSupport {
    private static final MailboxUser you = Mailbox.getOrCreateUser("you", "secret");
    private static final MailboxUser camelPipes = Mailbox.getOrCreateUser("camel|pipes@riders.org", "camelPipes", "secret");
    private static final MailboxUser easyPipes = Mailbox.getOrCreateUser("easyPipes@riders.org", "easyPipes", "secret");

    @Test
    public void testMultiRecipients() throws Exception {
        Mailbox.clearAll();

        sendBody("direct:a", "Camel does really rock");

        Mailbox inbox = camelPipes.getInbox();
        Message msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelPipes.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyPipes.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());

        inbox = easyPipes.getInbox();
        msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelPipes.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyPipes.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String recipients = "&to=" + camelPipes.getEmail() + ";" + easyPipes.getEmail();

                from("direct:a").to(you.uriPrefix(Protocol.smtp) + "&from=" + you.getEmail() + recipients);
            }
        };
    }
}
