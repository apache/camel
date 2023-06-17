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

import java.util.Locale;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for Camel html attachments and Mail attachments.
 */
public class MailHtmlAttachmentTest extends CamelTestSupport {
    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");

    @Test
    public void testSendAndReceiveMailWithAttachments() throws Exception {
        // clear mailbox
        Mailbox.clearAll();

        // START SNIPPET: e1

        // create an exchange with a normal body and attachment to be produced as email
        Endpoint endpoint = context.getEndpoint(james.uriPrefix(Protocol.smtp) + "&contentType=text/html");

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
        in.setBody("<html><body><h1>Hello</h1>World</body></html>");
        in.addAttachment("logo.jpeg", new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));

        // create a producer that can produce the exchange (= send the mail)
        Producer producer = endpoint.createProducer();
        // start the producer
        producer.start();
        // and let it go (processes the exchange by sending the email)
        producer.process(exchange);

        // END SNIPPET: e1

        // need some time for the mail to arrive on the inbox (consumed and sent to the mock)
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied(5000);
        Exchange out = mock.getReceivedExchanges().get(0);
        // plain text
        assertEquals("<html><body><h1>Hello</h1>World</body></html>", out.getIn().getBody(String.class));

        // attachment
        Map<String, DataHandler> attachments = out.getIn(AttachmentMessage.class).getAttachments();
        assertNotNull(attachments, "Should have attachments");
        assertEquals(1, attachments.size());

        DataHandler handler = out.getIn(AttachmentMessage.class).getAttachment("logo.jpeg");
        assertNotNull(handler, "The logo should be there");
        byte[] bytes = context.getTypeConverter().convertTo(byte[].class, handler.getInputStream());
        assertNotNull(bytes, "content should be there");
        assertTrue(bytes.length > 1000, "logo should be more than 1000 bytes");

        // content type should match
        boolean match1 = "image/jpeg; name=logo.jpeg".equals(handler.getContentType().toLowerCase(Locale.ROOT));
        boolean match2 = "application/octet-stream; name=logo.jpeg".equals(handler.getContentType().toLowerCase(Locale.ROOT));
        assertTrue(match1 || match2, "Should match 1 or 2");

        // save logo for visual inspection
        template.sendBodyAndHeader("file://target", bytes, Exchange.FILE_NAME, "maillogo.jpg");

        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(james.uriPrefix(Protocol.imap) + "&initialDelay=100&delay=100&closeFolder=false").to("mock:result");
            }
        };
    }
}
