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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the {@link SplitAttachmentsExpression}.
 */
public class MailSplitAttachmentsTest extends CamelTestSupport {
    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");

    private Endpoint endpoint;
    private SplitAttachmentsExpression splitAttachmentsExpression;
    private Exchange exchange;

    @BeforeEach
    public void clearMailBox() {
        Mailbox.clearAll();
    }

    @BeforeEach
    public void setup() {
        // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
        endpoint = context.getEndpoint(james.uriPrefix(Protocol.smtp));
        exchange = endpoint.createExchange();
        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
        in.setBody("Hello World");
        in.addAttachment("logo.jpeg", new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));
        in.addAttachment("log4j2.properties", new DataHandler(new FileDataSource("src/test/resources/log4j2.properties")));
    }

    @Test
    public void testExtractAttachments() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);

        mock.assertIsSatisfied();

        AttachmentMessage first = mock.getReceivedExchanges().get(0).getIn(AttachmentMessage.class);
        AttachmentMessage second = mock.getReceivedExchanges().get(1).getIn(AttachmentMessage.class);

        // check it's no longer an attachment, but is the message body
        assertEquals(0, first.getAttachments().size());
        assertEquals(0, second.getAttachments().size());

        assertEquals("logo.jpeg", first.getHeader("CamelSplitAttachmentId"));
        assertEquals("log4j2.properties", second.getHeader("CamelSplitAttachmentId"));

        byte[] expected1 = IOUtils.toByteArray(new FileDataSource("src/test/data/logo.jpeg").getInputStream());
        byte[] expected2 = Files.readString(Paths.get("src/test/resources/log4j2.properties"), StandardCharsets.UTF_8)
                .replace("\n", "\r\n").trim().getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected1, first.getBody(byte[].class));
        assertArrayEquals(expected2, second.getBody(byte[].class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        splitAttachmentsExpression = new SplitAttachmentsExpression();

        return new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: e1
                from(james.uriPrefix(Protocol.imap) + "&initialDelay=100&delay=100")
                        .to("log:email")
                        // use the SplitAttachmentsExpression which will split the message per attachment
                        .split(splitAttachmentsExpression)
                        // each message going to this mock has a single attachment
                        .to("mock:split")
                        .end();
                // END SNIPPET: e1
            }
        };
    }
}
