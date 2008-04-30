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
import javax.mail.internet.MimeMessage;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for processOnlyUnseenMessages option.
 */
public class MailProcessOnlyUnseenMessagesTest extends ContextTestSupport {

    public void testProcessOnlyUnseenMessages() throws Exception {
        prepareMailbox();

        sendBody("direct:a", "Message 3");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Message 3");
        mock.assertIsSatisfied();
    }

    private void prepareMailbox() throws Exception {
        JavaMailConnection connection = new JavaMailConnection();
        connection.setProtocol("pop3");
        connection.setHost("localhost");
        connection.setPort(25);
        connection.setUsername("claus");

        // inserts two messages with the SEEN flag
        Folder folder = connection.getFolder("pop3", "INBOX");
        folder.open(Folder.READ_WRITE);
        Message[] msg = new Message[2];
        msg[0] = new MimeMessage(connection.getSession());
        msg[0].setText("Message 1");
        msg[0].setFlag(Flags.Flag.SEEN, true);
        msg[1] = new MimeMessage(connection.getSession());
        msg[1].setText("Message 2");
        msg[1].setFlag(Flags.Flag.SEEN, true);
        folder.appendMessages(msg);
        folder.close(true);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("smtp://claus@localhost");

                // START SNIPPET: e1
                // consume only new unseen massages from the mailbox and poll the mailbox with 60 seconds interval
                long delay = 60 * 1000L;
                from("pop3://localhost?username=claus&password=secret&processOnlyUnseenMessages=true&consumer.delay=" + delay).
                        to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
