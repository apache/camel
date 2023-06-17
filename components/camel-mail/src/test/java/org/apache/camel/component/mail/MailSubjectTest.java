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

import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for Mail subject support.
 */
public class MailSubjectTest extends CamelTestSupport {
    private static final MailboxUser james2 = Mailbox.getOrCreateUser("james2", "secret");
    private String subject = "Camel rocks";

    @Test
    public void testMailSubject() throws Exception {
        Mailbox.clearAll();

        String body = "Hello Claus.\r\nYes it does.\r\n\r\nRegards James.";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("subject", subject);
        mock.expectedBodiesReceived(body);

        template.sendBody("direct:a", body);

        mock.assertIsSatisfied();

        assertFalse(mock.getExchanges().get(0).getIn(AttachmentMessage.class).hasAttachments(), "Should not have attachements");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("direct:a").setHeader("subject", constant(subject)).to(james2.uriPrefix(Protocol.smtp));
                // END SNIPPET: e1

                from(james2.uriPrefix(Protocol.imap) + "&initialDelay=100&delay=100").to("mock:result");
            }
        };
    }
}
