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

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for moveTo.
 */
public class MailMoveToTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testMoveToWithMarkAsSeen() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);
        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        assertEquals(0, Mailbox.get("jones@localhost").size());
        assertEquals(0, Mailbox.get("jones@localhost").getNewMessageCount());
        assertEquals(5, Mailbox.get("moveToFolder-jones@localhost").getNewMessageCount());

        Assert.assertTrue(Mailbox.get("moveToFolder-jones@localhost").get(0).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertTrue(Mailbox.get("moveToFolder-jones@localhost").get(1).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertTrue(Mailbox.get("moveToFolder-jones@localhost").get(2).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertTrue(Mailbox.get("moveToFolder-jones@localhost").get(3).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertTrue(Mailbox.get("moveToFolder-jones@localhost").get(4).getFlags().contains(Flags.Flag.SEEN));
    }

    @Test
    public void testMoveToWithDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedMessageCount(5);
        assertMockEndpointsSatisfied();

        Thread.sleep(500);

        assertEquals(0, Mailbox.get("jones2@localhost").size());
        assertEquals(0, Mailbox.get("jones2@localhost").getNewMessageCount());
        assertEquals(5, Mailbox.get("moveToFolder-jones2@localhost").getNewMessageCount());

        Assert.assertFalse(Mailbox.get("moveToFolder-jones2@localhost").get(0).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertFalse(Mailbox.get("moveToFolder-jones2@localhost").get(1).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertFalse(Mailbox.get("moveToFolder-jones2@localhost").get(2).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertFalse(Mailbox.get("moveToFolder-jones2@localhost").get(3).getFlags().contains(Flags.Flag.SEEN));
        Assert.assertFalse(Mailbox.get("moveToFolder-jones2@localhost").get(4).getFlags().contains(Flags.Flag.SEEN));
    }

    private void prepareMailbox() throws Exception {
        Mailbox.clearAll();
        String[] mailUser = new String[] {"jones", "jones2"};
        for (String username : mailUser) {
            JavaMailSender sender = new DefaultJavaMailSender();
            Store store = sender.getSession().getStore("pop3");
            store.connect("localhost", 25, username, "secret");
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            folder.expunge();

            // inserts 5 new messages
            Message[] messages = new Message[5];
            for (int i = 0; i < 5; i++) {
                messages[i] = new MimeMessage(sender.getSession());
                messages[i].setHeader("Message-ID", "" + i);
                messages[i].setText("Message " + i);
            }
            folder.appendMessages(messages);
            folder.close(true);
        }
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                from("imap://jones@localhost?password=secret&delete=false&moveTo=moveToFolder&initialDelay=100&delay=100").to("mock:result");
            }
        }, new RouteBuilder() {
            public void configure() throws Exception {
                from("imap://jones2@localhost?password=secret&delete=true&moveTo=moveToFolder&initialDelay=100&delay=100").to("mock:result2");
            }
        } };
    }
}
