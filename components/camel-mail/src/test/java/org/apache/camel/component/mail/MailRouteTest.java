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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.jvnet.mock_javamail.Mailbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailRouteTest extends CamelTestSupport {

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("hello world!");

        Map<String, Object> headers = new HashMap<>();
        headers.put(MailConstants.MAIL_REPLY_TO, "route-test-reply@localhost");
        template.sendBodyAndHeaders("smtp://route-test-james@localhost", "hello world!", headers);

        // lets test the first sent worked
        assertMailboxReceivedMessages("route-test-james@localhost");

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        // Validate that the headers were preserved.
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        String replyTo = (String) exchange.getIn().getHeader(MailConstants.MAIL_REPLY_TO);
        assertEquals("route-test-reply@localhost", replyTo);

        assertMailboxReceivedMessages("route-test-copy@localhost");
    }

    @Test
    public void testMailSubjectWithUnicode() throws Exception {
        Mailbox.clearAll();

        final String body = "Hello Camel Riders!";
        final String subject = "My Camel \u2122";

        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        // now we don't use the UTF-8 encoding
        mock.expectedHeaderReceived("subject", "=?US-ASCII?Q?My_Camel_=3F?=");
        mock.expectedBodiesReceived(body);

        template.send("direct:a", new Processor() {

            public void process(Exchange exchange) {
                exchange.getIn().setBody(body);
                exchange.getIn().setHeader("subject", subject);
                exchange.setProperty(Exchange.CHARSET_NAME, "US-ASCII");
            }
        });

        mock.assertIsSatisfied();

        assertFalse(mock.getExchanges().get(0).getIn(AttachmentMessage.class).hasAttachments(), "Should not have attachements");

    }

    protected void assertMailboxReceivedMessages(String name) throws IOException, MessagingException {
        Mailbox mailbox = Mailbox.get(name);
        assertEquals(1, mailbox.size(), name + " should have received 1 mail");

        Message message = mailbox.get(0);
        assertNotNull(message, name + " should have received at least one mail!");
        assertEquals("hello world!", message.getContent());
        assertEquals("camel@localhost", message.getFrom()[0].toString());
        boolean found = false;
        for (Address adr : message.getRecipients(RecipientType.TO)) {
            if (name.equals(adr.toString())) {
                found = true;
            }
        }
        assertTrue(found, "Should have found the recpient to in the mail: " + name);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pop3://route-test-james@localhost?initialDelay=100&delay=100").to("direct:a");

                // must use fixed to option to send the mail to the given
                // receiver, as we have polled
                // a mail from a mailbox where it already has the 'old' To as
                // header value
                // here we send the mail to 2 receivers. notice we can use a
                // plain string with semi colon
                // to seperate the mail addresses
                from("direct:a")
                        .setHeader("to", constant("route-test-result@localhost; route-test-copy@localhost"))
                        .to("smtp://localhost");

                from("pop3://route-test-result@localhost?initialDelay=100&delay=100").convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

}
