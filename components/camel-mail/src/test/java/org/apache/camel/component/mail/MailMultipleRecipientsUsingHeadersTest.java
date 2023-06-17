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

/**
 * Unit test for Mail using camel headers to set recipeient subject.
 */
public class MailMultipleRecipientsUsingHeadersTest extends CamelTestSupport {
    private static final MailboxUser claus = Mailbox.getOrCreateUser("claus", "secret");
    private static final MailboxUser jon = Mailbox.getOrCreateUser("jon", "secret");

    @Test
    public void testMailMultipleRecipientUsingHeaders() throws Exception {
        Mailbox.clearAll();

        // START SNIPPET: e1
        Map<String, Object> map = new HashMap<>();

        map.put("To", new String[] { claus.getEmail(), jon.getEmail() });
        map.put("From", "jstrachan@apache.org");
        map.put("Subject", "Camel rocks");

        String body = "Hello Riders.\nYes it does.\n\nRegards James.";
        template.sendBodyAndHeaders(claus.uriPrefix(Protocol.smtp), body, map);
        // END SNIPPET: e1

        Mailbox box = claus.getInbox();
        Message msg = box.get(0);
        assertEquals(claus.getEmail(), msg.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(jon.getEmail(), msg.getRecipients(Message.RecipientType.TO)[1].toString());
        assertEquals("jstrachan@apache.org", msg.getFrom()[0].toString());
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
