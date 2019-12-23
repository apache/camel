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
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class MimeMessageConsumeTest extends CamelTestSupport {
    private String body = "hello world!";

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        Session session = Session.getInstance(properties, null);

        MimeMessage message = new MimeMessage(session);
        populateMimeMessageBody(message);
        message.setRecipients(Message.RecipientType.TO, "james3@localhost");

        Transport.send(message);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        String text = exchange.getIn().getBody(String.class);
        assertEquals("mail body", body, text);

        assertNotNull("attachments got lost", exchange.getIn(AttachmentMessage.class).getAttachments());
        for (String s : exchange.getIn(AttachmentMessage.class).getAttachmentNames()) {
            DataHandler dh = exchange.getIn(AttachmentMessage.class).getAttachment(s);
            Object content = dh.getContent();
            assertNotNull("Content should not be empty", content);
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
                from("pop3://james3@localhost?initialDelay=100&delay=100").removeHeader("to").to("smtp://james4@localhost");
                from("pop3://james4@localhost?initialDelay=200&delay=100").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
