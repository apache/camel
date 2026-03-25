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
package org.apache.camel.component.google.mail.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GoogleMailStreamConsumerHeadersTest {

    private CamelContext camelContext;
    private GoogleMailStreamConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        GoogleMailStreamComponent component = new GoogleMailStreamComponent(camelContext);
        camelContext.addComponent("google-mail-stream", component);

        GoogleMailStreamEndpoint endpoint = (GoogleMailStreamEndpoint) camelContext
                .getEndpoint(
                        "google-mail-stream://test?clientId=a&clientSecret=b&applicationName=c&accessToken=d&refreshToken=e");

        consumer = new GoogleMailStreamConsumer(endpoint, exchange -> {
        }, "UNREAD", Collections.emptyList());
        consumer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void testThreadIdHeader() {
        Message mail = createTestMessage("msg-123", "thread-456");
        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        assertEquals("thread-456", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_THREAD_ID));
    }

    @Test
    void testMessageIdHeader() {
        Message mail = createTestMessage("msg-123", "thread-456");
        addHeader(mail, "Message-ID", "<abc123@mail.gmail.com>");

        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        assertEquals("<abc123@mail.gmail.com>", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_MESSAGE_ID));
    }

    @Test
    void testAllHeaders() {
        Message mail = createTestMessage("msg-123", "thread-456");
        mail.setLabelIds(List.of("INBOX", "UNREAD"));
        addHeader(mail, "Subject", "Test Subject");
        addHeader(mail, "To", "to@example.com");
        addHeader(mail, "From", "from@example.com");
        addHeader(mail, "Cc", "cc@example.com");
        addHeader(mail, "Bcc", "bcc@example.com");
        addHeader(mail, "Message-ID", "<test@example.com>");

        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        assertEquals("msg-123", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_ID));
        assertEquals("thread-456", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_THREAD_ID));
        assertEquals(List.of("INBOX", "UNREAD"), exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_LABEL_IDS));
        assertEquals("Test Subject", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_SUBJECT));
        assertEquals("to@example.com", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_TO));
        assertEquals("from@example.com", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_FROM));
        assertEquals("cc@example.com", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_CC));
        assertEquals("bcc@example.com", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_BCC));
        assertEquals("<test@example.com>", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_MESSAGE_ID));
    }

    @Test
    void testLabelIdsHeader() {
        Message mail = createTestMessage("msg-123", "thread-456");
        List<String> labels = List.of("INBOX", "UNREAD", "CATEGORY_PERSONAL");
        mail.setLabelIds(labels);

        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        List<String> result = exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_LABEL_IDS, List.class);
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("INBOX", result.get(0));
        assertEquals("UNREAD", result.get(1));
        assertEquals("CATEGORY_PERSONAL", result.get(2));
    }

    @Test
    void testNullLabelIdsHeader() {
        Message mail = createTestMessage("msg-123", "thread-456");

        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        assertNull(exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_LABEL_IDS));
    }

    @Test
    void testMissingMessageIdHeader() {
        Message mail = createTestMessage("msg-123", "thread-456");

        Exchange exchange = consumer.createExchange(ExchangePattern.InOnly, mail);

        assertNull(exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_MESSAGE_ID));
    }

    private Message createTestMessage(String id, String threadId) {
        Message mail = new Message();
        mail.setId(id);
        mail.setThreadId(threadId);

        MessagePartBody body = new MessagePartBody();
        body.setData(null);

        MessagePart payload = new MessagePart();
        payload.setHeaders(new ArrayList<>());
        payload.setParts(null);
        payload.setBody(body);

        mail.setPayload(payload);
        return mail;
    }

    private void addHeader(Message mail, String name, String value) {
        List<MessagePartHeader> headers = mail.getPayload().getHeaders();
        MessagePartHeader header = new MessagePartHeader();
        header.setName(name);
        header.setValue(value);
        headers.add(header);
    }
}
