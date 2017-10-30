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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test with poll enrich
 */
public class MailPollEnrichTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testPollEnrich() throws Exception {
        Mailbox mailbox = Mailbox.get("bill@localhost");
        assertEquals(5, mailbox.size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Message 0");

        template.sendBody("direct:start", "");

        mock.assertIsSatisfied();
    }

    @Test
    public void testPollEnrichNullBody() throws Exception {
        Mailbox mailbox = Mailbox.get("bill@localhost");
        assertEquals(5, mailbox.size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Message 0");

        template.sendBody("direct:start", null);

        mock.assertIsSatisfied();
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

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .pollEnrich("pop3://bill@localhost?password=secret&consumer.initialDelay=100&consumer.delay=100", 5000)
                    .to("log:mail", "mock:result");
            }
        };
    }

}
