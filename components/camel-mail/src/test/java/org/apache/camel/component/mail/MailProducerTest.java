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

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MailProducerTest extends CamelTestSupport {
    private static final MailboxUser camel = Mailbox.getOrCreateUser("camel", "secret");
    private static final MailboxUser someone = Mailbox.getOrCreateUser("someone", "secret");
    private static final MailboxUser recipient2 = Mailbox.getOrCreateUser("recipient2", "secret");

    @Test
    public void testProducer() throws Exception {
        Mailbox.clearAll();
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Message ", "To", "someone@localhost");
        MockEndpoint.assertIsSatisfied(context);
        // need to check the message header
        Exchange exchange = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull(exchange.getIn().getHeader(MailConstants.MAIL_MESSAGE_ID), "The message id should not be null");

        Mailbox box = someone.getInbox();
        assertEquals(1, box.getMessageCount());
    }

    @Test
    public void testProducerBodyIsMimeMessage() throws Exception {
        Mailbox.clearAll();
        getMockEndpoint("mock:result").expectedMessageCount(1);

        Address from = new InternetAddress("fromCamelTest@localhost");
        Address to = new InternetAddress(recipient2.getEmail());
        Session session = Mailbox.getSmtpSession();
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(from);
        mimeMessage.addRecipient(RecipientType.TO, to);
        mimeMessage.setSubject("This is the subject.");
        mimeMessage.setText("This is the message");

        template.sendBodyAndHeader("direct:start", mimeMessage, "To", "someone@localhost");
        MockEndpoint.assertIsSatisfied(context);
        // need to check the message header
        Exchange exchange = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull(exchange.getIn().getHeader(MailConstants.MAIL_MESSAGE_ID), "The message id should not be null");

        Mailbox box = someone.getInbox();
        assertEquals(0, box.getMessageCount());

        // Check if the mimeMessagea has override body and headers
        Mailbox box2 = recipient2.getInbox();
        assertEquals(1, box2.getMessageCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(camel.uriPrefix(Protocol.smtp), "mock:result");
            }
        };
    }

}
