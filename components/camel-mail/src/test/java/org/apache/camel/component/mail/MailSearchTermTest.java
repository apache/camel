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

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.SearchTerm;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.mail.SearchTermBuilder.Op;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MailSearchTermTest extends CamelTestSupport {
    protected static final MailboxUser bill = Mailbox.getOrCreateUser("bill", "secret");

    @BindToRegistry("myTerm")
    private SearchTerm term = createSearchTerm();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    protected SearchTerm createSearchTerm() {
        // we just want the unseen Camel related mails
        SearchTermBuilder build = new SearchTermBuilder();
        build.subject("Camel").body(Op.or, "Camel").unseen();

        return build.build();
    }

    @Test
    public void testSearchTerm() throws Exception {
        Mailbox mailbox = bill.getInbox();
        assertEquals(6, mailbox.getMessageCount());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("I like riding the Camel", "Ordering Camel in Action");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", Mailbox.getPort(Protocol.imap), bill.getLogin(), bill.getPassword());
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        // inserts 5 new messages
        Message[] messages = new Message[6];
        messages[0] = new MimeMessage(sender.getSession());
        messages[0].setSubject("Apache Camel rocks");
        messages[0].setText("I like riding the Camel");
        messages[0].setHeader("Message-ID", "0");
        messages[0].setFrom(new InternetAddress("someone@somewhere.com"));

        messages[1] = new MimeMessage(sender.getSession());
        messages[1].setSubject("Order");
        messages[1].setText("Ordering Camel in Action");
        messages[1].setHeader("Message-ID", "1");
        messages[1].setFrom(new InternetAddress("dude@somewhere.com"));

        messages[2] = new MimeMessage(sender.getSession());
        messages[2].setSubject("Order");
        messages[2].setText("Ordering ActiveMQ in Action");
        messages[2].setHeader("Message-ID", "2");
        messages[2].setFrom(new InternetAddress("dude@somewhere.com"));

        messages[3] = new MimeMessage(sender.getSession());
        messages[3].setSubject("Buy pharmacy");
        messages[3].setText("This is spam");
        messages[3].setHeader("Message-ID", "3");
        messages[3].setFrom(new InternetAddress("spam@me.com"));

        messages[4] = new MimeMessage(sender.getSession());
        messages[4].setSubject("Beers tonight?");
        messages[4].setText("We meet at 7pm the usual place");
        messages[4].setHeader("Message-ID", "4");
        messages[4].setFrom(new InternetAddress("barney@simpsons.com"));

        messages[5] = new MimeMessage(sender.getSession());
        messages[5].setSubject("Spambot attack");
        messages[5].setText("I am attaching you");
        messages[5].setHeader("Message-ID", "5");
        messages[5].setFrom(new InternetAddress("spambot@me.com"));

        folder.appendMessages(messages);
        folder.close(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(bill.uriPrefix(Protocol.imap) + "&debugMode=false&searchTerm=#myTerm&initialDelay=100&delay=100")
                        .to("mock:result");
            }
        };
    }

}
