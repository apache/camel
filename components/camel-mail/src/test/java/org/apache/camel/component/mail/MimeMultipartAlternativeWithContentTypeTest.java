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

import java.io.ByteArrayOutputStream;

import jakarta.mail.internet.MimeMultipart;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mail.MailConstants.MAIL_ALTERNATIVE_BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MimeMultipartAlternativeWithContentTypeTest extends CamelTestSupport {
    private static final MailboxUser sachin = Mailbox.getOrCreateUser("sachin", "secret");
    private Logger log = LoggerFactory.getLogger(getClass());
    private String alternativeBody = "hello world! (plain text)";
    private String htmlBody = "<html><body><h1>Hello</h1>World</body></html>";

    private void sendMultipartEmail() {
        Mailbox.clearAll();

        // create an exchange with a normal body and attachment to be produced as email
        MailEndpoint endpoint = context.getEndpoint(
                sachin.uriPrefix(Protocol.smtp) + "&contentType=text/html; charset=UTF-8", MailEndpoint.class);
        endpoint.getConfiguration().setAlternativeBodyHeader(MailConstants.MAIL_ALTERNATIVE_BODY);

        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        Exchange exchange = endpoint.createExchange();
        Message in = exchange.getIn();
        in.setBody(htmlBody);
        in.setHeader(MAIL_ALTERNATIVE_BODY, alternativeBody);

        // create a producer that can produce the exchange (= send the mail)
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(MailConstants.MAIL_ALTERNATIVE_BODY).isNull();

        context.createProducerTemplate().send(endpoint, exchange);
    }

    private void verifyTheRecivedEmail(String expectString) throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.assertIsSatisfied();

        Exchange out = mock.assertExchangeReceived(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(((MailMessage) out.getIn()).getMessage().getSize());
        ((MailMessage) out.getIn()).getMessage().writeTo(baos);
        String dumpedMessage = baos.toString();
        assertTrue(dumpedMessage.indexOf(expectString) > 0, "There should have the " + expectString);
        log.trace("multipart alternative: \n{}", dumpedMessage);

        // plain text
        assertEquals(alternativeBody, out.getIn().getBody(String.class));

        assertEquals(2, out.getIn().getBody(MimeMultipart.class).getCount(), "multipart body should have 2 parts");
    }

    @Test
    public void testMultipartEmailContentType() throws Exception {
        sendMultipartEmail();
        verifyTheRecivedEmail("Content-Type: text/plain; charset=UTF-8");
        verifyTheRecivedEmail("Content-Type: text/html; charset=UTF-8");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(sachin.uriPrefix(Protocol.imap)
                     + "&initialDelay=100&delay=100&closeFolder=false&contentType=text/html; charset=UTF-8")
                        .to("mock:result");
            }
        };
    }
}
