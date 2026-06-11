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

import java.util.Map;

import jakarta.mail.Message;

import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the trust-boundary hardening options (useHeaderRecipients, useHeaderFrom, useHeaderSubject) which prevent
 * incoming headers from overriding pre-configured endpoint URI values.
 */
public class MailHeaderOverrideOptionsTest extends CamelTestSupport {

    private static final MailboxUser sender = Mailbox.getOrCreateUser("hdrOpts-sender", "secret");
    private static final MailboxUser epRcpt = Mailbox.getOrCreateUser("hdrOpts-ep-rcpt", "secret");
    private static final MailboxUser hdrRcpt = Mailbox.getOrCreateUser("hdrOpts-hdr-rcpt", "secret");
    private final String baseUri = sender.uriPrefix(Protocol.smtp);

    @BeforeEach
    public void setup() {
        Mailbox.clearAll();
    }

    @Test
    public void testUseHeaderRecipientsTrue_headerWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderRecipients=true&to=" + epRcpt.getEmail(), "Camel rocks",
                Map.of("To", hdrRcpt.getEmail()));
        assertEquals(1, hdrRcpt.getInbox().getMessageCount());
        assertEquals(0, epRcpt.getInbox().getMessageCount());
        assertEquals(hdrRcpt.getEmail(), hdrRcpt.getInbox().get(0).getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @Test
    public void testUseHeaderRecipientsFalse_endpointWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderRecipients=false&to=" + epRcpt.getEmail(), "Camel rocks",
                Map.of("To", hdrRcpt.getEmail()));
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals(0, hdrRcpt.getInbox().getMessageCount());
        assertEquals(epRcpt.getEmail(), epRcpt.getInbox().get(0).getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @Test
    public void testUseHeaderRecipientsTrue_noHeaderFallsBackToEndpoint() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderRecipients=true&to=" + epRcpt.getEmail(), "Camel rocks", Map.of());
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals(epRcpt.getEmail(), epRcpt.getInbox().get(0).getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @Test
    public void testUseHeaderFromTrue_headerWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderFrom=true&from=ep-from@example.com&to=" + epRcpt.getEmail(),
                "Camel rocks", Map.of("From", "hdr-from@example.com"));
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals("hdr-from@example.com", epRcpt.getInbox().get(0).getFrom()[0].toString());
    }

    @Test
    public void testUseHeaderFromFalse_endpointWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderFrom=false&from=ep-from@example.com&to=" + epRcpt.getEmail(),
                "Camel rocks", Map.of("From", "hdr-from@example.com"));
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals("ep-from@example.com", epRcpt.getInbox().get(0).getFrom()[0].toString());
    }

    @Test
    public void testUseHeaderSubjectTrue_headerWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderSubject=true&subject=endpoint-subject&to=" + epRcpt.getEmail(),
                "Camel rocks", Map.of("Subject", "header-subject"));
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals("header-subject", epRcpt.getInbox().get(0).getSubject());
    }

    @Test
    public void testUseHeaderSubjectFalse_endpointWins() throws Exception {
        template.sendBodyAndHeaders(baseUri + "&useHeaderSubject=false&subject=endpoint-subject&to=" + epRcpt.getEmail(),
                "Camel rocks", Map.of("Subject", "header-subject"));
        assertEquals(1, epRcpt.getInbox().getMessageCount());
        assertEquals("endpoint-subject", epRcpt.getInbox().get(0).getSubject());
    }
}
