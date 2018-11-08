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
import javax.mail.internet.MimeMessage;

import com.sun.mail.imap.SortTerm;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * This is a test that checks integration of the sort term in Camel. The actual sorting logic is tested in the
 * SortUtilTest.
 */
public class MailSortTermTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("sortAscendingDate", new SortTerm[]{SortTerm.DATE});
        jndi.bind("sortDescendingDate", new SortTerm[]{SortTerm.REVERSE, SortTerm.DATE});
        jndi.bind("searchTerm", new SearchTermBuilder().subject("Camel").build());
        return jndi;
    }

    @Test
    public void testSortTerm() throws Exception {
        Mailbox mailbox = Mailbox.get("bill@localhost");
        assertEquals(3, mailbox.size());

        // This one has search term *not* set
        MockEndpoint mockAsc = getMockEndpoint("mock:resultAscending");
        mockAsc.expectedBodiesReceived("Earlier date", "Later date");

        context.startAllRoutes();

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

        // inserts 3 messages, one with earlier, one with later sent date and one with invalid subject (not returned in search)
        Message[] messages = new Message[3];
        messages[0] = new MimeMessage(sender.getSession());
        messages[0].setText("Earlier date");
        messages[0].setHeader("Message-ID", "0");
        messages[0].setSentDate(new Date(10000));
        messages[0].setSubject("Camel");

        messages[1] = new MimeMessage(sender.getSession());
        messages[1].setText("Later date");
        messages[1].setHeader("Message-ID", "1");
        messages[1].setSentDate(new Date(20000));
        messages[1].setSubject("Camel");

        messages[2] = new MimeMessage(sender.getSession());
        messages[2].setText("Even later date");
        messages[2].setHeader("Message-ID", "2");
        messages[2].setSentDate(new Date(30000));
        messages[2].setSubject("Invalid");

        folder.appendMessages(messages);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                context.setAutoStartup(false);

                from("pop3://bill@localhost?password=secret&searchTerm=#searchTerm&sortTerm=#sortAscendingDate&consumer.initialDelay=100&consumer.delay=100").to("mock:resultAscending");
            }
        };
    }

}
