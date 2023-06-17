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

import java.io.File;
import java.net.URISyntaxException;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.activation.URLDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MimeMessageConsumeTest extends CamelTestSupport {
    private static final MailboxUser james3 = Mailbox.getOrCreateUser("james3", "secret");
    private static final MailboxUser james4 = Mailbox.getOrCreateUser("james4", "secret");
    private String body = "hello world!";

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        Session session = Mailbox.getSmtpSession();

        MimeMessage message = new MimeMessage(session);
        populateMimeMessageBody(message);
        message.setRecipients(Message.RecipientType.TO, "james3@localhost");

        Transport.send(message, james3.getLogin(), james3.getPassword());

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        String text = exchange.getIn().getBody(String.class);
        assertEquals(body, text, "mail body");

        assertNotNull(exchange.getIn(AttachmentMessage.class).getAttachments(), "attachments got lost");
        for (String s : exchange.getIn(AttachmentMessage.class).getAttachmentNames()) {
            DataHandler dh = exchange.getIn(AttachmentMessage.class).getAttachment(s);
            Object content = dh.getContent();
            assertNotNull(content, "Content should not be empty");
            assertEquals("log4j2.properties", dh.getName());
        }
    }

    /**
     * Lets encode a multipart mime message
     */
    protected void populateMimeMessageBody(MimeMessage message) throws MessagingException {
        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(body);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setText("<html><body>" + body + "</body></html>");

        Multipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(plainPart);
        alt.addBodyPart(htmlPart);

        Multipart mixed = new MimeMultipart("mixed");
        MimeBodyPart wrap = new MimeBodyPart();
        wrap.setContent(alt);
        mixed.addBodyPart(wrap);

        mixed.addBodyPart(plainPart);
        mixed.addBodyPart(htmlPart);

        DataSource ds;
        try {
            File f = new File(getClass().getResource("/log4j2.properties").toURI());
            ds = new FileDataSource(f);
        } catch (URISyntaxException ex) {
            ds = new URLDataSource(getClass().getResource("/log4j2.properties"));
        }
        DataHandler dh = new DataHandler(ds);

        BodyPart attachmentBodyPart;
        // Create another body part
        attachmentBodyPart = new MimeBodyPart();
        // Set the data handler to the attachment
        attachmentBodyPart.setDataHandler(dh);
        // Set the filename
        attachmentBodyPart.setFileName(dh.getName());
        // Set Disposition
        attachmentBodyPart.setDisposition(Part.ATTACHMENT);

        mixed.addBodyPart(plainPart);
        mixed.addBodyPart(htmlPart);
        // Add attachmentBodyPart to multipart
        mixed.addBodyPart(attachmentBodyPart);

        message.setContent(mixed);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(james3.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100").removeHeader("to")
                        .to(james4.uriPrefix(Protocol.smtp));
                from(james4.uriPrefix(Protocol.pop3) + "&initialDelay=200&delay=100").convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
