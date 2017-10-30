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

import javax.mail.internet.InternetAddress;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for Mail replyTo support.
 */
public class MailReplyToTest extends CamelTestSupport {
    
    @Test
    public void testMailReplyTo() throws Exception {
        Mailbox.clearAll();

        String body = "The Camel riders";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("Reply-To", "noReply1@localhost,noReply2@localhost");
        mock.expectedBodiesReceived(body);

        template.sendBodyAndHeader("direct:a", body, "Reply-To", "noReply1@localhost,noReply2@localhost");

        mock.assertIsSatisfied();

        Mailbox mailbox = Mailbox.get("christian@localhost");
        assertEquals(1, mailbox.size());
        assertEquals("noReply1@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[0]).getAddress());
        assertEquals("noReply2@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[1]).getAddress());
        assertEquals(body, mailbox.get(0).getContent());
    }
    
    @Test
    public void testMailReplyTo2() throws Exception {
        Mailbox.clearAll();

        String body = "The Camel riders";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("Reply-To", "noReply1@localhost, noReply2@localhost");
        mock.expectedBodiesReceived(body);

        template.sendBody("direct:b", body);

        mock.assertIsSatisfied();

        Mailbox mailbox = Mailbox.get("christian@localhost");
        assertEquals(1, mailbox.size());
        assertEquals("noReply1@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[0]).getAddress());
        assertEquals("noReply2@localhost", ((InternetAddress) mailbox.get(0).getReplyTo()[1]).getAddress());
        assertEquals(body, mailbox.get(0).getContent());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a")
                    .to("smtp://christian@localhost?subject=Camel");
                
                from("direct:b")
                    .to("smtp://christian@localhost?subject=Camel&replyTo=noReply1@localhost,noReply2@localhost");

                from("pop3://localhost?username=christian&password=secret&consumer.initialDelay=100&consumer.delay=100")
                    .to("mock:result");
            }
        };
    }
}
