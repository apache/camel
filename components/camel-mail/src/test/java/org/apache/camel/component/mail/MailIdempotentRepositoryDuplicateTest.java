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

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for idempotent repository.
 */
public class MailIdempotentRepositoryDuplicateTest extends CamelTestSupport {

    MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    @Override
    public void setUp() throws Exception {
        // lets assume this ID is already done
        myRepo.add("myuid-3");

        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testIdempotent() throws Exception {
        assertEquals(1, myRepo.getCacheSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        // no 3 is already in the idempotent repo
        mock.expectedBodiesReceived("Message 0", "Message 1", "Message 2", "Message 4");

        context.startRoute("foo");

        assertMockEndpointsSatisfied();

        // windows need a little slack
        Thread.sleep(500);

        assertEquals(0, Mailbox.get("jones@localhost").getNewMessageCount());

        // they are removed on confirm
        assertEquals(1, myRepo.getCacheSize());
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        Mailbox.clearAll();
        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("pop3");
        store.connect("localhost", 25, "jones", "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        // inserts 5 new messages
        Message[] messages = new Message[5];
        for (int i = 0; i < 5; i++) {
            messages[i] = new MimeMessage(sender.getSession());
            messages[i].setText("Message " + i);
            messages[i].setHeader("Message-ID", "myuid-" + i);
        }
        folder.appendMessages(messages);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("imap://jones@localhost?password=secret&idempotentRepository=#myRepo&consumer.initialDelay=100&consumer.delay=100").routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
