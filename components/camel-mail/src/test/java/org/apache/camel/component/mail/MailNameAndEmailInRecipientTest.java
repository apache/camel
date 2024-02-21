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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class MailNameAndEmailInRecipientTest extends CamelTestSupport {
    private static final MailboxUser davsclaus = Mailbox.getOrCreateUser("davsclaus", "secret");
    private static final MailboxUser jstrachan = Mailbox.getOrCreateUser("jstrachan", "secret");

    @Test
    public void testSendWithNameAndEmailInRecipient() throws Exception {
        Mailbox.clearAll();

        // START SNIPPET: e1
        Map<String, Object> headers = new HashMap<>();
        headers.put("to", "Claus Ibsen <davsclaus@localhost>");
        headers.put("cc", "James Strachan <jstrachan@localhost>");

        assertMailbox("davsclaus");
        assertMailbox("jstrachan");

        template.sendBodyAndHeaders(davsclaus.uriPrefix(Protocol.smtp), "Hello World", headers);
        // END SNIPPET: e1

        MockEndpoint.assertIsSatisfied(context);
    }

    private void assertMailbox(String name) {
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedBodiesReceived("Hello World\r\n");
        mock.message(0).header("to").isEqualTo("Claus Ibsen <davsclaus@localhost>");
        mock.message(0).header("cc").isEqualTo("James Strachan <jstrachan@localhost>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(davsclaus.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100").to("mock:davsclaus");

                from(jstrachan.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100").to("mock:jstrachan");
            }
        };
    }

}
