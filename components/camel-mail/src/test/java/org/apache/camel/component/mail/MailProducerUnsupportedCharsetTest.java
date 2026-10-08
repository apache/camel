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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
public class MailProducerUnsupportedCharsetTest extends CamelTestSupport {
    private static final MailboxUser jones1 = Mailbox.getOrCreateUser("MailProducerUnsupportedCharsetTest-jones1", "secret");
    private static final MailboxUser jones2 = Mailbox.getOrCreateUser("MailProducerUnsupportedCharsetTest-jones2", "secret");

    @Override
    public void setupResources() throws Exception {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    public void testSendUnsupportedCharset() throws Exception {
        jones1.clear();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(jones1.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100&ignoreUnsupportedCharset=true")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World\r\n", "Bye World\r\n");
        mock.allMessages().header("Content-Type").isEqualTo("text/plain");

        Map<String, Object> headers = new HashMap<>();
        headers.put("To", jones1.getEmail());
        headers.put("Content-Type", "text/plain");
        template.sendBodyAndHeaders(jones1.uriPrefix(Protocol.smtp) + "&ignoreUnsupportedCharset=true", "Hello World", headers);

        headers.clear();
        headers.put("To", jones1.getEmail());
        headers.put("Content-Type", "text/plain; charset=XXX");
        template.sendBodyAndHeaders(jones1.uriPrefix(Protocol.smtp) + "&ignoreUnsupportedCharset=true", "Bye World", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSendUnsupportedCharsetDisabledOption() throws Exception {
        jones2.clear();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(jones2.uriPrefix(Protocol.pop3) + "&initialDelay=100&delay=100&ignoreUnsupportedCharset=false")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World\r\n");
        mock.allMessages().header("Content-Type").isEqualTo("text/plain");

        Map<String, Object> headers = new HashMap<>();
        headers.put("To", jones2.getEmail());
        headers.put("Content-Type", "text/plain");
        template.sendBodyAndHeaders(jones2.uriPrefix(Protocol.smtp) + "&ignoreUnsupportedCharset=false", "Hello World",
                headers);

        headers.clear();
        headers.put("To", jones2.getEmail());
        headers.put("Content-Type", "text/plain; charset=XXX");

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> template.sendBodyAndHeaders(jones2.uriPrefix(Protocol.smtp) + "&ignoreUnsupportedCharset=false",
                        "Bye World",
                        headers),
                "Should have thrown an exception");
        assertIsInstanceOf(UnsupportedEncodingException.class, e.getCause());

        MockEndpoint.assertIsSatisfied(context);
    }

}
