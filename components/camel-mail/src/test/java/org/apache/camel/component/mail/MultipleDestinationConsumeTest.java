/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Header;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;

/**
 * @version $Revision: 1.1 $
 */
public class MultipleDestinationConsumeTest extends ContextTestSupport {
    protected MockEndpoint resultEndpoint;
    protected String body = "hello world!";
    protected Session mailSession;

    public void testSendAndReceiveMails() throws Exception {
        resultEndpoint = (MockEndpoint) resolveMandatoryEndpoint("mock:result");
        resultEndpoint.expectedMinimumMessageCount(1);

        MimeMessage message = new MimeMessage(mailSession);
        message.setText(body);

        message.setRecipients(Message.RecipientType.TO, new Address[]{
                new InternetAddress("james@localhost"), new InternetAddress("bar@localhost")
        });

        Transport.send(message);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);

        org.apache.camel.Message in = exchange.getIn();
        log.debug("Received: " + in.getBody());

        String text = in.getBody(String.class);
        log.debug("Has headers: " + in.getHeaders());

        MailExchange mailExchange = (MailExchange) exchange;
        Message inMessage = mailExchange.getIn().getMessage();
        Enumeration iter = inMessage.getAllHeaders();
        while (iter.hasMoreElements()) {
            Header header = (Header) iter.nextElement();
            String[] value = message.getHeader(header.getName());
            log.debug("Header: " + header.getName() + " has value: " + ObjectHelper.asString(value));
        }

        assertEquals("body", body, text);
        Object value = in.getHeader("TO");
        assertEquals("TO Header", "james@localhost, bar@localhost", value);
/*
        List list = assertIsInstanceOf(List.class, value);
        assertEquals("to list", 2, list.size());
*/
    }

    @Override
    protected void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        mailSession = Session.getInstance(properties, null);

        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("smtp://james@localhost?password=foo").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}