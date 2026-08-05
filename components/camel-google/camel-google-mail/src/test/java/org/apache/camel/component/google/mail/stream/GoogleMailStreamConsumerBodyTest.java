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

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies how the stream consumer turns a Gmail message into an exchange: which format it asks the API for, and where
 * it picks the body up from.
 */
class GoogleMailStreamConsumerBodyTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleMailStreamConsumer consumer(boolean raw) throws Exception {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();
        context.start();
        GoogleMailStreamEndpoint endpoint = context.getEndpoint(
                "google-mail-stream://index?clientId=id&clientSecret=secret&raw=" + raw,
                GoogleMailStreamEndpoint.class);
        return new GoogleMailStreamConsumer(endpoint, exchange -> {
        }, "UNREAD", List.of());
    }

    private static MessagePartBody body(String content) {
        return new MessagePartBody().setData(Base64.encodeBase64URLSafeString(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theRawOptionAsksForTheRawFormat() throws Exception {
        // the raw field of a message is only returned for the RAW format, asking for FULL always left it null
        assertThat(consumer(true).messageFormat()).isEqualTo("RAW");
        assertThat(consumer(false).messageFormat()).isEqualTo("FULL");
    }

    @Test
    void aNonMultipartMessageKeepsItsBody() throws Exception {
        Message mail = new Message().setId("1").setThreadId("t1")
                .setPayload(new MessagePart().setMimeType("text/plain").setBody(body("plain content")));

        Exchange exchange = consumer(false).createExchange(ExchangePattern.InOnly, mail);

        assertThat(exchange.getIn().getBody()).isEqualTo("plain content");
    }

    @Test
    void aMultipartMessageUsesTheFirstPartCarryingData() throws Exception {
        Message mail = new Message().setId("2").setPayload(new MessagePart().setMimeType("multipart/alternative")
                .setParts(List.of(
                        new MessagePart().setMimeType("multipart/mixed")
                                .setParts(List.of(new MessagePart().setMimeType("text/plain").setBody(body("nested")))),
                        new MessagePart().setMimeType("text/html").setBody(body("<p>html</p>")))));

        Exchange exchange = consumer(false).createExchange(ExchangePattern.InOnly, mail);

        assertThat(exchange.getIn().getBody()).isEqualTo("nested");
    }

    @Test
    void aMessageWithoutPayloadIsNotAFailure() throws Exception {
        Message mail = new Message().setId("3");

        Exchange exchange = consumer(false).createExchange(ExchangePattern.InOnly, mail);

        assertThat(exchange.getIn().getBody()).isNull();
        assertThat(exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_ID)).isEqualTo("3");
    }

    @Test
    void headersAreMappedWhenPresent() throws Exception {
        Message mail = new Message().setId("4").setPayload(new MessagePart()
                .setBody(body("content"))
                .setHeaders(List.of(
                        new MessagePartHeader().setName("Subject").setValue("a subject"),
                        new MessagePartHeader().setName("From").setValue("someone@example.org"))));

        Exchange exchange = consumer(false).createExchange(ExchangePattern.InOnly, mail);

        assertThat(exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_SUBJECT)).isEqualTo("a subject");
        assertThat(exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_FROM)).isEqualTo("someone@example.org");
    }

    @Test
    void aPayloadWithoutHeadersIsNotAFailure() throws Exception {
        Message mail = new Message().setId("5").setPayload(new MessagePart().setBody(body("content")));

        assertThatCode(() -> consumer(false).createExchange(ExchangePattern.InOnly, mail)).doesNotThrowAnyException();
    }
}
