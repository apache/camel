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
package org.apache.camel.component.google.mail.transform;

import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.MimeUtility;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.Draft;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_BCC;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_CC;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_FROM;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_MESSAGE_ID;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_SUBJECT;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_THREAD_ID;
import static org.apache.camel.component.google.mail.stream.GoogleMailStreamConstants.MAIL_TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleMailDraftDataTypeTransformerTest extends CamelTestSupport {

    private GoogleMailDraftDataTypeTransformer transformer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .transformDataType("google-mail:draft")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected void doPostSetup() {
        transformer = new GoogleMailDraftDataTypeTransformer();
        transformer.setCamelContext(context);
    }

    @Test
    void testBasicDraftCreation() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("This is a test draft message");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();
        assertThat(draft.getMessage()).isNotNull();
        assertThat(draft.getMessage().getRaw()).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("This is a test draft message")
                .contains("Content-Type: text/plain; charset=UTF-8");
    }

    @Test
    void testDraftWithThreadingMetadata() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Reply message");
        exchange.getMessage().setHeader(MAIL_THREAD_ID, "thread-123");
        exchange.getMessage().setHeader(MAIL_MESSAGE_ID, "<msg-456@mail.gmail.com>");
        exchange.getMessage().setHeader(MAIL_FROM, "sender@example.com");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Re: Test Subject");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();
        assertThat(draft.getMessage()).isNotNull();
        assertThat(draft.getMessage().getThreadId()).isEqualTo("thread-123");

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("From: sender@example.com")
                .contains("Subject: Re: Test Subject")
                .contains("In-Reply-To: <msg-456@mail.gmail.com>")
                .contains("References: <msg-456@mail.gmail.com>")
                .contains("Reply message");
    }

    @Test
    void testDraftWithAllHeaders() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Complete message");
        exchange.getMessage().setHeader(MAIL_FROM, "from@example.com");
        exchange.getMessage().setHeader(MAIL_TO, "to@example.com");
        exchange.getMessage().setHeader(MAIL_CC, "cc@example.com");
        exchange.getMessage().setHeader(MAIL_BCC, "bcc@example.com");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Complete Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("From: from@example.com")
                .contains("To: to@example.com")
                .contains("Cc: cc@example.com")
                .contains("Bcc: bcc@example.com")
                .contains("Subject: Complete Test")
                .contains("Complete message");
    }

    @Test
    void testDraftWithEmptyBody() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Empty Body Test");

        assertThatThrownBy(() -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDraftWithNullBody() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Null Body Test");

        assertThatThrownBy(() -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDraftWithoutThreadId() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("New conversation");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "New Thread");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();
        assertThat(draft.getMessage().getThreadId()).isNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("Subject: New Thread")
                .contains("New conversation");
    }

    @Test
    void testDraftWithHtmlBodyRequiresExplicitContentType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        String htmlBody = "<html><body><h1>Hello</h1><p>This is HTML</p></body></html>";
        exchange.getMessage().setBody(htmlBody);
        exchange.getMessage().setHeader(MAIL_SUBJECT, "HTML Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .as("Without explicit Content-Type header, should default to text/plain even for HTML-like body")
                .contains("text/plain");
    }

    @Test
    void testDraftWithHtmlBodyAndExplicitHtmlContentType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        String htmlBody = "<html><body><h1>Hello</h1><p>This is HTML</p></body></html>";
        exchange.getMessage().setBody(htmlBody);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/html; charset=UTF-8");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "HTML Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("text/html")
                .contains(htmlBody);
    }

    @Test
    void testDraftWithExplicitContentTypeHeader() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Some plain text");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/html");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Explicit CT");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .as("MimeMessage should set text/html and determine charset automatically")
                .contains("Content-Type: text/html");
    }

    @Test
    void testDraftWithPlainTextDefaultContentType() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Just plain text, no HTML");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Plain Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage).contains("Content-Type: text/plain; charset=UTF-8");
    }

    @Test
    void testDraftWithNonAsciiSubject() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Message with accented subject");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Resumé for café meeting");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("Subject: =?UTF-8?")
                .contains("?=")
                .contains("Message with accented subject");
        String decodedSubject = MimeUtility.decodeText(
                decodedMessage.lines().filter(l -> l.startsWith("Subject:")).findFirst().orElse(""));
        assertThat(decodedSubject)
                .as("Non-ASCII characters should be preserved after decoding")
                .contains("Resumé for café meeting");
    }

    @Test
    void testDraftWithNonAsciiFrom() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Test message");
        exchange.getMessage().setHeader(MAIL_FROM, "Renée Müller <renee@example.com>");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .as("MimeMessage should handle non-ASCII in From display name and preserve the address")
                .contains("From:")
                .contains("renee@example.com");
    }

    @Test
    void testDraftWithAsciiHeadersNotEncoded() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Test message");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Plain ASCII Subject");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage).contains("Subject: Plain ASCII Subject");
    }

    @Test
    void testDraftWithExplicitContentTypeIncludingCharset() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Some text");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/html; charset=ISO-8859-1");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("Content-Type: text/html; charset=ISO-8859-1")
                .doesNotContain("charset=UTF-8");
    }

    // E2E tests via Camel route

    @Test
    void testDraftCreationViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.getMessage().setBody("Route test message");
            exchange.getMessage().setHeader(MAIL_FROM, "route@example.com");
            exchange.getMessage().setHeader(MAIL_SUBJECT, "Route Test");
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        Draft draft = received.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();
        assertThat(draft.getMessage()).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("From: route@example.com")
                .contains("Subject: Route Test")
                .contains("Route test message");
    }

    @Test
    void testReplyDraftViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.getMessage().setBody("This is a reply");
            exchange.getMessage().setHeader(MAIL_THREAD_ID, "thread-reply-123");
            exchange.getMessage().setHeader(MAIL_MESSAGE_ID, "<original@mail.gmail.com>");
            exchange.getMessage().setHeader(MAIL_FROM, "replier@example.com");
            exchange.getMessage().setHeader(MAIL_SUBJECT, "Re: Original Subject");
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        Draft draft = received.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();
        assertThat(draft.getMessage().getThreadId()).isEqualTo("thread-reply-123");

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("In-Reply-To: <original@mail.gmail.com>")
                .contains("References: <original@mail.gmail.com>")
                .contains("This is a reply");
    }

    @Test
    void testMultilineBodyViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        String multilineBody = "Line 1\nLine 2\nLine 3\n\nLine 5";
        template.send("direct:input", exchange -> {
            exchange.getMessage().setBody(multilineBody);
            exchange.getMessage().setHeader(MAIL_SUBJECT, "Multiline Test");
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        Draft draft = received.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("Line 1")
                .contains("Line 2")
                .contains("Line 3")
                .contains("Line 5");
    }

    @Test
    void testDraftWithMalformedEmailAddress() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Test message");
        exchange.getMessage().setHeader(MAIL_TO, "not a valid@ address");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Malformed Test");

        assertThatThrownBy(() -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testDraftWithMultipleRecipients() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Message to multiple recipients");
        exchange.getMessage().setHeader(MAIL_TO, "user1@example.com, user2@example.com");
        exchange.getMessage().setHeader(MAIL_CC, "cc1@example.com, cc2@example.com");
        exchange.getMessage().setHeader(MAIL_BCC, "bcc1@example.com");
        exchange.getMessage().setHeader(MAIL_SUBJECT, "Multiple Recipients Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Draft draft = exchange.getMessage().getBody(Draft.class);
        assertThat(draft).isNotNull();

        String decodedMessage = decodeMessage(draft.getMessage().getRaw());
        assertThat(decodedMessage)
                .contains("To: user1@example.com")
                .contains("user2@example.com")
                .contains("Cc: cc1@example.com")
                .contains("cc2@example.com")
                .contains("Bcc: bcc1@example.com")
                .contains("Subject: Multiple Recipients Test")
                .contains("Message to multiple recipients");
    }

    // Helper method to decode Base64 message
    private String decodeMessage(String encodedMessage) {
        byte[] decodedBytes = Base64.decodeBase64(encodedMessage);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}
