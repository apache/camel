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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit test to verify that message headers override pre configuration.
 */
public class MailHeaderOverrulePreConfigurationRecipientsTest extends CamelTestSupport {
    private static final MailboxUser claus = Mailbox.getOrCreateUser("claus", "secret");
    private static final MailboxUser willem = Mailbox.getOrCreateUser("willem", "secret");

    @Test
    public void testSendWithRecipientsInHeaders() throws Exception {
        Mailbox.clearAll();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World\r\n");
        mock.expectedHeaderReceived("to", claus.getEmail());
        mock.expectedHeaderReceived("cc", willem.getEmail());

        Map<String, Object> headers = new HashMap<>();
        headers.put("to", claus.getEmail());
        headers.put("cc", willem.getEmail());

        template.sendBodyAndHeaders(claus.uriPrefix(Protocol.smtp), "Hello World", headers);

        mock.assertIsSatisfied();
        /* Bcc should be stripped by specs compliant SMTP servers */
        Assertions.assertThat(mock.getReceivedExchanges().get(0).getMessage().getHeader("bcc")).isNull();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(claus.uriPrefix(Protocol.pop3) + "&to=someone@outhere.com&cc=none@world.com&initialDelay=100&delay=100")
                        .to("mock:result");
            }
        };
    }

}
