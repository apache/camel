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

import jakarta.mail.Message;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for recipients (To, CC, BCC)
 */
public class MailRecipientsTest extends CamelTestSupport {
    private static final MailboxUser you = Mailbox.getOrCreateUser("youRecipients", "secret");
    private static final MailboxUser camelRiders
            = Mailbox.getOrCreateUser("camelRecipients@riders.org", "camelRecipients", "secret");
    private static final MailboxUser easyRiders
            = Mailbox.getOrCreateUser("easyRecipients@riders.org", "easyRecipients", "secret");
    private static final MailboxUser me = Mailbox.getOrCreateUser("meRecipients@you.org", "meRecipients", "secret");
    private static final MailboxUser someone
            = Mailbox.getOrCreateUser("someoneRecipients@somewhere.org", "someoneRecipients", "secret");
    private static final MailboxUser to = Mailbox.getOrCreateUser("toRecipients@somewhere.org", "toRecipients", "secret");

    @Test
    public void testMultiRecipients() throws Exception {
        Mailbox.clearAll();

        sendBody("direct:a", "Camel does really rock");

        Mailbox inbox = camelRiders.getInbox();
        Message msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());

        /* Bcc should be stripped by specs compliant SMTP servers */
        Assertions.assertThat(msg.getRecipients(Message.RecipientType.BCC)).isNull();

        inbox = easyRiders.getInbox();
        msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());
        /* Bcc should be stripped by specs compliant SMTP servers */
        Assertions.assertThat(msg.getRecipients(Message.RecipientType.BCC)).isNull();

        inbox = me.getInbox();
        msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());
        /* Bcc should be stripped by specs compliant SMTP servers */
        Assertions.assertThat(msg.getRecipients(Message.RecipientType.BCC)).isNull();

        inbox = someone.getInbox();
        msg = inbox.get(0);
        assertEquals(you.getEmail(), msg.getFrom()[0].toString());
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());
        /* Bcc should be stripped by specs compliant SMTP servers */
        Assertions.assertThat(msg.getRecipients(Message.RecipientType.BCC)).isNull();
    }

    @Test
    public void testHeadersBlocked() throws Exception {
        Mailbox.clearAll();

        // direct:b blocks all message headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("to", to.getEmail());
        headers.put("cc", "header@riders.org");

        template.sendBodyAndHeaders("direct:b", "Hello World", headers);

        Mailbox box = camelRiders.getInbox();
        Message msg = box.get(0);
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(easyRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());
    }

    @Test
    public void testSpecificHeaderBlocked() throws Exception {
        Mailbox.clearAll();

        // direct:c blocks the "cc" message header - so only "to" will be used here
        Map<String, Object> headers = new HashMap<>();
        headers.put("to", to.getEmail());
        headers.put("cc", "header@riders.org");

        template.sendBodyAndHeaders("direct:c", "Hello World", headers);

        Mailbox box = to.getInbox();
        Message msg = box.get(0);
        assertEquals(to.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertNull(msg.getRecipients(Message.RecipientType.CC));
    }

    @Test
    public void testSpecificHeaderBlockedInjection() throws Exception {
        Mailbox.clearAll();

        // direct:c blocks the "cc" message header - but we are trying to inject cc in via another header
        Map<String, Object> headers = new HashMap<>();
        headers.put("blah", "somevalue\r\ncc: injected@riders.org");

        template.sendBodyAndHeaders("direct:c", "Hello World", headers);

        Mailbox box = camelRiders.getInbox();
        Message msg = box.get(0);
        assertEquals(camelRiders.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(1, msg.getRecipients(Message.RecipientType.CC).length);
        assertEquals(me.getEmail(), msg.getRecipients(Message.RecipientType.CC)[0].toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                // all the recipients of this mail are:
                // to: camel@riders.org , easy@riders.org
                // cc: me@you.org
                // bcc: someone@somewhere.org
                String recipients = "&to=" + camelRiders.getEmail() + "," + easyRiders.getEmail() + "&cc=" + me.getEmail()
                                    + "&bcc=" + someone.getEmail();

                from("direct:a").to(you.uriPrefix(Protocol.smtp) + "&from=" + you.getEmail() + recipients);
                from("direct:b").removeHeaders("*")
                        .to(you.uriPrefix(Protocol.smtp) + "&from=" + you.getEmail() + recipients);
                from("direct:c").removeHeaders("cc")
                        .to(you.uriPrefix(Protocol.smtp) + "&from=" + you.getEmail() + recipients);
                // END SNIPPET: e1
            }
        };
    }
}
