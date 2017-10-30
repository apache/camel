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

import java.util.Date;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class MailSearchTermUriConfigLast24HoursTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testSearchTerm() throws Exception {
        Mailbox mailbox = Mailbox.get("bill@localhost");
        assertEquals(6, mailbox.size());

        // should only get the 4 latest emails that was sent within the last 24 hours
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Ordering ActiveMQ in Action", "This is spam",
                "We meet at 7pm the usual place", "I am attaching you");

        assertMockEndpointsSatisfied();
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("pop3");
        store.connect("localhost", 25, "bill", "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        long twoDaysAgo = new Date().getTime() - 2 * 24 * 60 * 60 * 1000L;
        long twentyHoursAgo = new Date().getTime() - 1 * 20 * 60 * 60 * 1000L;
        long oneHourAgo = new Date().getTime() - 1 * 1 * 60 * 60 * 1000L;

        // inserts 5 new messages
        Message[] messages = new Message[6];
        messages[0] = new MimeMessage(sender.getSession());
        messages[0].setSubject("Apache Camel rocks");
        messages[0].setText("I like riding the Camel");
        messages[0].setHeader("Message-ID", "0");
        messages[0].setFrom(new InternetAddress("someone@somewhere.com"));
        messages[0].setSentDate(new Date(twoDaysAgo));

        messages[1] = new MimeMessage(sender.getSession());
        messages[1].setSubject("Order");
        messages[1].setText("Ordering Camel in Action");
        messages[1].setFrom(new InternetAddress("dude@somewhere.com"));
        messages[1].setHeader("Message-ID", "1");
        messages[1].setSentDate(new Date(twoDaysAgo));

        messages[2] = new MimeMessage(sender.getSession());
        messages[2].setSubject("Order");
        messages[2].setText("Ordering ActiveMQ in Action");
        messages[2].setHeader("Message-ID", "2");
        messages[2].setFrom(new InternetAddress("dude@somewhere.com"));
        messages[2].setSentDate(new Date(twentyHoursAgo));

        messages[3] = new MimeMessage(sender.getSession());
        messages[3].setSubject("Buy pharmacy");
        messages[3].setText("This is spam");
        messages[3].setHeader("Message-ID", "3");
        messages[3].setFrom(new InternetAddress("spam@me.com"));
        messages[3].setSentDate(new Date(twentyHoursAgo));

        messages[4] = new MimeMessage(sender.getSession());
        messages[4].setSubject("Beers tonight?");
        messages[4].setText("We meet at 7pm the usual place");
        messages[4].setHeader("Message-ID", "4");
        messages[4].setFrom(new InternetAddress("barney@simpsons.com"));
        messages[4].setSentDate(new Date(oneHourAgo));

        messages[5] = new MimeMessage(sender.getSession());
        messages[5].setSubject("Spambot attack");
        messages[5].setText("I am attaching you");
        messages[5].setHeader("Message-ID", "5");
        messages[5].setFrom(new InternetAddress("spambot@me.com"));
        messages[5].setSentDate(new Date());
        messages[5].setSentDate(new Date(oneHourAgo));

        folder.appendMessages(messages);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://bill@localhost?password=secret&searchTerm.fromSentDate=now-24h&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }

}
