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

import java.io.IOException;
import java.util.HashMap;

import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.jvnet.mock_javamail.Mailbox;

import static org.apache.camel.util.ObjectHelper.asString;

/**
 * @version $Revision: 1.1 $
 */
public class MailRouteTest extends ContextTestSupport {
    private MockEndpoint resultEndpoint;

    public void testSendAndReceiveMails() throws Exception {
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("hello world!");

        HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put("reply-to", "route-test-reply@localhost");
        template.sendBodyAndHeaders("smtp://route-test-james@localhost", "hello world!", headers);

        // lets test the first sent worked
        assertMailboxReceivedMessages("route-test-james@localhost");

        // lets sleep to check that the mail poll does not redeliver duplicate
        // mails
        Thread.sleep(3000);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        // Validate that the headers were preserved.
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        String replyTo = (String)exchange.getIn().getHeader("reply-to");
        assertEquals("route-test-reply@localhost", replyTo);

        assertMailboxReceivedMessages("route-test-copy@localhost");
    }

    protected void assertMailboxReceivedMessages(String name) throws IOException, MessagingException {
        Mailbox mailbox = Mailbox.get(name);
        assertEquals(name + " should have received 1 mail", 1, mailbox.size());

        Message message = mailbox.get(0);
        assertNotNull(name + " should have received at least one mail!", message);
        logMessage(message);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("smtp://route-test-james@localhost").to("direct:a");
                from("direct:a").to("smtp://route-test-result@localhost", "smtp://route-test-copy@localhost");
                from("smtp://route-test-result@localhost").convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    protected void logMessage(Message message) throws IOException, MessagingException {
        log.info("Received: " + message.getContent() + " from: " + asString(message.getFrom()) + " to: " + asString(message.getRecipients(RecipientType.TO)));
    }
}
