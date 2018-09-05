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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * @version 
 */
public class MultipleDestinationConsumeTest extends CamelTestSupport {
    private String body = "hello world!";
    private Session mailSession;

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        MimeMessage message = new MimeMessage(mailSession);
        message.setText(body);

        message.setRecipients(Message.RecipientType.TO,
                              new Address[] {new InternetAddress("james@localhost"),
                                             new InternetAddress("bar@localhost")});

        Transport.send(message);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied(100000);

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        org.apache.camel.Message in = exchange.getIn();
        assertNotNull("Should have headers", in.getHeaders());

        MailMessage msg = (MailMessage) exchange.getIn();
        Message inMessage = msg != null ? msg.getMessage() : null;
        assertNotNull("In message has no JavaMail message!", inMessage);

        String text = in.getBody(String.class);
        assertEquals("mail body", body, text);

        // need to use iterator as some mail impl returns String[] and others a single String with comma as separator
        // so we let Camel create an iterator so we can use the same code for the test
        Object to = in.getHeader("TO");
        Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(to));
        int i = 0;
        while (it.hasNext()) {
            if (i == 0) {
                assertEquals("james@localhost", it.next().trim());
            } else {
                assertEquals("bar@localhost", it.next().trim());
            }
            i++;
        }

        Enumeration<Header> iter = CastUtils.cast(inMessage.getAllHeaders());
        while (iter.hasMoreElements()) {
            Header header = iter.nextElement();
            String[] value = message.getHeader(header.getName());
            log.debug("Header: " + header.getName() + " has value: " + ObjectHelper.asString(value));
        }
    }

    @Override
    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        mailSession = Session.getInstance(properties, null);

        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pop3://james@localhost?password=foo&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }
}