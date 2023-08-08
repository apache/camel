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

import java.util.Enumeration;
import java.util.Iterator;

import jakarta.mail.Address;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.CastUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MultipleDestinationConsumeTest extends CamelTestSupport {
    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");
    private static final MailboxUser bar = Mailbox.getOrCreateUser("bar", "secret");
    private Logger log = LoggerFactory.getLogger(getClass());
    private String body = "hello world!\r\n";
    private Session mailSession;

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        MimeMessage message = new MimeMessage(mailSession);
        message.setText(body);

        message.setRecipients(Message.RecipientType.TO,
                new Address[] {
                        new InternetAddress(james.getEmail()),
                        new InternetAddress(bar.getEmail()) });

        Transport.send(message);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied(100000);

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        org.apache.camel.Message in = exchange.getIn();
        assertNotNull(in.getHeaders(), "Should have headers");

        MailMessage msg = (MailMessage) exchange.getIn();
        Message inMessage = msg != null ? msg.getMessage() : null;
        assertNotNull(inMessage, "In message has no JavaMail message!");

        String text = in.getBody(String.class);
        assertEquals(body, text, "mail body");

        // need to use iterator as some mail impl returns String[] and others a single String with comma as separator
        // so we let Camel create an iterator so we can use the same code for the test
        Object to = in.getHeader("TO");
        Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(to));
        int i = 0;
        while (it.hasNext()) {
            if (i == 0) {
                assertEquals(james.getEmail(), it.next().trim());
            } else {
                assertEquals(bar.getEmail(), it.next().trim());
            }
            i++;
        }

        Enumeration<Header> iter = CastUtils.cast(inMessage.getAllHeaders());
        while (iter.hasMoreElements()) {
            Header header = iter.nextElement();
            String[] value = message.getHeader(header.getName());
            log.debug("Header: {} has value: {}", header.getName(), org.apache.camel.util.ObjectHelper.asString(value));
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        mailSession = Mailbox.getSmtpSession();

        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(james.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100&closeFolder=false").to("mock:result");
            }
        };
    }
}
