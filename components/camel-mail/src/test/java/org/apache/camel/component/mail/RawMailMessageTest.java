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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for Mail using camel headers to set recipient subject.
 */
public class RawMailMessageTest extends CamelTestSupport {

    private static final MailboxUser jonesPop3 = Mailbox.getOrCreateUser("jonesPop3", "secret");
    private static final MailboxUser jonesRawPop3 = Mailbox.getOrCreateUser("jonesRawPop3", "secret");
    private static final MailboxUser jonesImap = Mailbox.getOrCreateUser("jonesImap", "secret");
    private static final MailboxUser jonesRawImap = Mailbox.getOrCreateUser("jonesRawImap", "secret");
    private static final MailboxUser davsclaus = Mailbox.getOrCreateUser("davsclaus", "secret");

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        Mailbox.clearAll();
        prepareMailbox(jonesPop3);
        prepareMailbox(jonesRawPop3);
        prepareMailbox(jonesImap);
        prepareMailbox(jonesRawImap);
        super.setUp();
    }

    @Test
    public void testGetRawJavaMailMessage() throws Exception {
        Mailbox.clearAll();

        Map<String, Object> map = new HashMap<>();
        map.put("To", davsclaus.getEmail());
        map.put("From", "jstrachan@apache.org");
        map.put("Subject", "Camel rocks");

        String body = "Hello Claus.\nYes it does.\n\nRegards James.";

        getMockEndpoint("mock:mail").expectedMessageCount(1);
        template.sendBodyAndHeaders(
                "smtp://davsclaus@localhost:" + Mailbox.getPort(Protocol.smtp) + "?password=" + davsclaus.getPassword(), body,
                map);
        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = getMockEndpoint("mock:mail").getReceivedExchanges().get(0);

        // START SNIPPET: e1
        // get access to the raw jakarta.mail.Message as shown below
        Message javaMailMessage = exchange.getIn(MailMessage.class).getMessage();
        assertNotNull(javaMailMessage, "The mail message should not be null");

        assertEquals("Camel rocks", javaMailMessage.getSubject());
        // END SNIPPET: e1
    }

    @Test
    public void testRawMessageConsumerPop3() throws Exception {
        testRawMessageConsumer("Pop3", jonesRawPop3);
    }

    @Test
    public void testRawMessageConsumerImap() throws Exception {
        testRawMessageConsumer("Imap", jonesRawImap);
    }

    private void testRawMessageConsumer(String type, MailboxUser user) throws Exception {
        Mailbox mailboxRaw = user.getInbox();
        assertEquals(1, mailboxRaw.getMessageCount(), "expected 1 message in the mailbox");

        MockEndpoint mock = getMockEndpoint("mock://rawMessage" + type);
        mock.expectedMessageCount(1);
        mock.message(0).body().isNotNull();

        MockEndpoint.assertIsSatisfied(context);

        Message mailMessage = mock.getExchanges().get(0).getIn().getBody(Message.class);
        assertNotNull("mail subject should not be null", mailMessage.getSubject());
        assertEquals("hurz", mailMessage.getSubject(), "mail subject should be hurz");

        Map<String, Object> headers = mock.getExchanges().get(0).getIn().getHeaders();
        assertNotNull(headers, "headers should not be null");
        assertFalse(headers.isEmpty(), "headers should not be empty");
    }

    @Test
    public void testNormalMessageConsumerPop3() throws Exception {
        testNormalMessageConsumer("Pop3", jonesPop3);
    }

    @Test
    public void testNormalMessageConsumerImap() throws Exception {
        testNormalMessageConsumer("Imap", jonesImap);
    }

    private void testNormalMessageConsumer(String type, MailboxUser user) throws Exception {
        Mailbox mailbox = user.getInbox();
        assertEquals(1, mailbox.getMessageCount(), "expected 1 message in the mailbox");

        MockEndpoint mock = getMockEndpoint("mock://normalMessage" + type);
        mock.expectedMessageCount(1);
        mock.message(0).body().isNotNull();

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getExchanges().get(0).getIn().getBody(String.class);
        MimeMessage mm = new MimeMessage(null, new ByteArrayInputStream(body.getBytes()));
        String subject = mm.getSubject();
        assertNull(subject, "mail subject should not be available");

        Map<String, Object> headers = mock.getExchanges().get(0).getIn().getHeaders();
        assertNotNull(headers, "headers should not be null");
        assertFalse(headers.isEmpty(), "headers should not be empty");
    }

    private void prepareMailbox(MailboxUser user) throws Exception {
        // connect to mailbox
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), user.getLogin(), user.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        InputStream is = getClass().getResourceAsStream("/SignedMailTestCaseHurz.txt");
        Message hurzMsg = new MimeMessage(sender.getSession(), is);
        Message[] messages = new Message[] { hurzMsg };

        // insert one signed message
        folder.appendMessages(messages);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(davsclaus.uriPrefix(Protocol.pop3) + "&closeFolder=false").to("mock:mail");

                from(jonesRawPop3.uriPrefix(Protocol.pop3)
                     + "&closeFolder=false&initialDelay=100&delay=100&delete=true&mapMailMessage=false")
                        .to("mock://rawMessagePop3");

                from(jonesImap.uriPrefix(Protocol.imap)
                     + "&closeFolder=false&initialDelay=100&delay=100&delete=true&mapMailMessage=false")
                        .to("mock://rawMessageImap");

                from(jonesPop3.uriPrefix(Protocol.pop3) + "&closeFolder=false&initialDelay=100&delay=100&delete=true")
                        .to("mock://normalMessagePop3");

                from(jonesImap.uriPrefix(Protocol.imap) + "&closeFolder=false&initialDelay=100&delay=100&delete=true")
                        .to("mock://normalMessageImap");
            }
        };
    }
}
