/**
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
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for Mail using camel headers to set recipient subject.
 */
public class RawMailMessageTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        Mailbox.clearAll();
        prepareMailbox("jonesPop3", "pop3");
        prepareMailbox("jonesRawPop3", "pop3");
        prepareMailbox("jonesImap", "imap");
        prepareMailbox("jonesRawImap", "imap");
        super.setUp();
    }

    @Test
    public void testGetRawJavaMailMessage() throws Exception {
        Mailbox.clearAll();

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("To", "davsclaus@apache.org");
        map.put("From", "jstrachan@apache.org");
        map.put("Subject", "Camel rocks");

        String body = "Hello Claus.\nYes it does.\n\nRegards James.";

        getMockEndpoint("mock:mail").expectedMessageCount(1);
        template.sendBodyAndHeaders("smtp://davsclaus@apache.org", body, map);
        assertMockEndpointsSatisfied();

        Exchange exchange = getMockEndpoint("mock:mail").getReceivedExchanges().get(0);

        // START SNIPPET: e1
        // get access to the raw javax.mail.Message as shown below
        Message javaMailMessage = exchange.getIn(MailMessage.class).getMessage();
        assertNotNull(javaMailMessage);

        assertEquals("Camel rocks", javaMailMessage.getSubject());
        // END SNIPPET: e1
    }

    @Test
    public void testRawMessageConsumerPop3() throws Exception {
        testRawMessageConsumer("Pop3");
    }

    @Test
    public void testRawMessageConsumerImap() throws Exception {
        testRawMessageConsumer("Imap");
    }

    private void testRawMessageConsumer(String type) throws Exception {
        Mailbox mailboxRaw = Mailbox.get("jonesRaw" + type + "@localhost");
        assertEquals(1, mailboxRaw.size());

        MockEndpoint mock = getMockEndpoint("mock://rawMessage" + type);
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body().isNotNull();
        assertMockEndpointsSatisfied();

        Message mailMessage = mock.getExchanges().get(0).getIn().getBody(Message.class);
        assertNotNull("mail subject should not be null", mailMessage.getSubject());
        assertEquals("mail subject should be hurz", "hurz", mailMessage.getSubject());

        Map<String, Object> headers = mock.getExchanges().get(0).getIn().getHeaders();
        assertNotNull(headers);
        assertTrue(!headers.isEmpty());
    }

    @Test
    public void testNormalMessageConsumerPop3() throws Exception {
        testNormalMessageConsumer("Pop3");
    }

    @Test
    public void testNormalMessageConsumerImap() throws Exception {
        testNormalMessageConsumer("Imap");
    }

    private void testNormalMessageConsumer(String type) throws Exception {
        Mailbox mailbox = Mailbox.get("jones" + type + "@localhost");
        assertEquals(1, mailbox.size());

        MockEndpoint mock = getMockEndpoint("mock://normalMessage" + type);
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body().isNotNull();
        assertMockEndpointsSatisfied();

        String body = mock.getExchanges().get(0).getIn().getBody(String.class);
        MimeMessage mm = new MimeMessage(null, new ByteArrayInputStream(body.getBytes()));
        String subject = mm.getSubject();
        assertNull("mail subject should not be available", subject);

        Map<String, Object> headers = mock.getExchanges().get(0).getIn().getHeaders();
        assertNotNull(headers);
        assertTrue(!headers.isEmpty());
    }

    private void prepareMailbox(String user, String type) throws Exception {
        // connect to mailbox
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore(type);
        store.connect("localhost", 25, user, "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        InputStream is = getClass().getResourceAsStream("/SignedMailTestCaseHurz.elm");
        Message hurzMsg = new MimeMessage(sender.getSession(), is);
        Message[] messages = new Message[] {hurzMsg};

        // insert one signed message
        folder.appendMessages(messages);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://davsclaus@apache.org").to("mock:mail");

                from("pop3://jonesRawPop3@localhost?password=secret&consumer.initialDelay=100&consumer.delay=100&delete=true&mapMailMessage=false")
                    .to("mock://rawMessagePop3");

                from("imap://jonesRawImap@localhost?password=secret&consumer.initialDelay=100&consumer.delay=100&delete=true&mapMailMessage=false")
                    .to("mock://rawMessageImap");

                from("pop3://jonesPop3@localhost?password=secret&consumer.initialDelay=100&consumer.delay=100&delete=true")
                    .to("mock://normalMessagePop3");

                from("imap://jonesImap@localhost?password=secret&consumer.initialDelay=100&consumer.delay=100&delete=true")
                    .to("mock://normalMessageImap");
            }
        };
    }
}