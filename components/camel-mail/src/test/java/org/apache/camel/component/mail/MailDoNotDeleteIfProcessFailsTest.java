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

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for rollback option.
 */
public class MailDoNotDeleteIfProcessFailsTest extends CamelTestSupport {

    private static int counter;

    @Override
    public void setUp() throws Exception {
        prepareMailbox();
        super.setUp();
    }

    @Test
    public void testRoolbackIfProcessFails() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Message 1");
        // the first 2 attempt should fail
        getMockEndpoint("mock:error").expectedMessageCount(2);

        assertMockEndpointsSatisfied();

        assertEquals(3, counter);
    }

    private void prepareMailbox() throws Exception {
        // connect to mailbox
        Mailbox.clearAll();

        JavaMailSender sender = new DefaultJavaMailSender();
        Store store = sender.getSession().getStore("imap");
        store.connect("localhost", 25, "claus", "secret");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.expunge();

        // inserts two new messages
        Message[] msg = new Message[2];
        msg[0] = new MimeMessage(sender.getSession());
        msg[0].setText("Message 1");
        msg[0].setHeader("Message-ID", "0");
        msg[0].setFlag(Flags.Flag.SEEN, false);
        msg[1] = new MimeMessage(sender.getSession());
        msg[1].setText("Message 2");
        msg[0].setHeader("Message-ID", "1");
        msg[1].setFlag(Flags.Flag.SEEN, true);
        folder.appendMessages(msg);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // no redelivery for unit test as we want it to be polled next time
                onException(IllegalArgumentException.class).to("mock:error");

                from("imap://localhost?username=claus&password=secret&unseen=true&consumer.initialDelay=100&consumer.delay=100")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                counter++;
                                if (counter < 3) {
                                    throw new IllegalArgumentException("Forced by unit test");
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }

}