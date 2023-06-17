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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for Mail using camel headers to set recipient subject.
 */
public class MailUsingHeadersTest extends CamelTestSupport {
    private static final MailboxUser davsclaus = Mailbox.getOrCreateUser("davsclaus", "secret");

    @Test
    public void testMailUsingHeaders() throws Exception {
        Mailbox.clearAll();

        // START SNIPPET: e1
        Map<String, Object> map = new HashMap<>();
        map.put("To", "davsclaus@localhost");
        map.put("From", "jstrachan@apache.org");
        map.put("Subject", "Camel rocks");
        map.put("CamelFileName", "fileOne");
        map.put("org.apache.camel.test", "value");

        String body = "Hello Claus.\nYes it does.\n\nRegards James.";
        template.sendBodyAndHeaders(davsclaus.uriPrefix(Protocol.smtp), body, map);
        // END SNIPPET: e1

        Mailbox box = davsclaus.getInbox();
        Message msg = box.get(0);
        assertEquals("davsclaus@localhost", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("jstrachan@apache.org", msg.getFrom()[0].toString());
        assertEquals("Camel rocks", msg.getSubject());

        assertNull(msg.getHeader("CamelFileName"), "We should not get the message header here");
        assertNull(msg.getHeader("org.apache.camel.test"), "We should not get the message header here");
    }

    @Test
    public void testMailWithFromInEndpoint() throws Exception {
        Mailbox.clearAll();

        Map<String, Object> map = new HashMap<>();
        map.put("Subject", "Camel rocks");

        String body = "Hello Claus.\nYes it does.\n\nRegards James.";
        template.sendBodyAndHeaders(
                davsclaus.uriPrefix(Protocol.smtp) + "&from=James Strachan <jstrachan@apache.org>&to=davsclaus@localhost", body,
                map);

        Mailbox box = davsclaus.getInbox();
        Message msg = box.get(0);
        assertEquals("davsclaus@localhost", msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("James Strachan <jstrachan@apache.org>", msg.getFrom()[0].toString());
        assertEquals("Camel rocks", msg.getSubject());

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // no routes
            }
        };
    }
}
