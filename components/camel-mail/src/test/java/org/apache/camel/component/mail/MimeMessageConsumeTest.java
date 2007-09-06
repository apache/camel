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

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 1.1 $
 */
public class MimeMessageConsumeTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String body = "hello world!";

    public void testSendAndReceiveMails() throws Exception {
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        Session session = Session.getDefaultInstance(properties, null);

        MimeMessage message = new MimeMessage(session);
        populateMimeMessageBody(message);
        message.setRecipients(Message.RecipientType.TO, "james@localhost");

        Transport.send(message);



        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        log.info("Received: " + exchange.getIn().getBody());

        String text = exchange.getIn().getBody(String.class);
        assertEquals("body", body, text);
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

        message.setContent(mixed);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("smtp://james@localhost").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
