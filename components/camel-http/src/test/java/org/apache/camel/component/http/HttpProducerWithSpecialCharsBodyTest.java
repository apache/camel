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
package org.apache.camel.component.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.apache.camel.Exchange.CHARSET_NAME;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpProducerWithSpecialCharsBodyTest {

    private static final String TEST_MESSAGE_WITH_SPECIAL_CHARACTERS
            = """
                    {
                        "description": "Example with special characters",
                        "BasicLatin": "\u0021 \u0023 \u0024 \u0025 \u0026 \u0027 \u0028 \u0029 \u002A \u002B \u002C \u002D \u002E \u002F \u003A \u003B \u003C \u003D \u003E \u003F",
                        "Latin1": "\u00A1 \u00BF \u00A9 \u00AE \u00F1 \u00F6 \u00FC",
                        "Greek": "\u0391 \u03B2 \u03B3 \u0394 \u03A9",
                        "Cyrillic": "\u0410 \u0431 \u0432 \u0413 \u0434",
                        "Hebrew": "\u05D0 \u05D1 \u05D2 \u05D3 \u05D4",
                        "Arabic": "\u0627 \u0628 \u062A \u062B \u062C",
                        "Devanagari": "\u0905 \u0906 \u0907 \u0908 \u0909",
                        "CJK Unified Ideographs" : "\u4E00 \u4E8C \u4E09 \u56DB \u4E94",
                        "Emoticons": "\uD83D\uDE00 \uD83D\uDE0E \uD83D\uDE0A \uD83C\uDF0D"
                    }
                    """;

    private static final String APPLICATION_JSON_UTF8
            = APPLICATION_JSON.getMimeType() + "; charset=" + StandardCharsets.UTF_8.name();

    @Test
    void createRequestEntityJsonUtf8ThroughContentType() throws CamelExchangeException, IOException {
        HttpEndpoint httpEndpoint = mock(HttpEndpoint.class);
        HttpProducer httpProducer = new HttpProducer(httpEndpoint);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(TEST_MESSAGE_WITH_SPECIAL_CHARACTERS);
        when(message.getHeader(Exchange.CONTENT_TYPE, String.class)).thenReturn(APPLICATION_JSON_UTF8);

        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);

        HttpEntity requestEntity = httpProducer.createRequestEntity(exchange);

        assertTrue(requestEntity instanceof StringEntity);
        StringEntity entity = (StringEntity) requestEntity;
        assertEquals(APPLICATION_JSON_UTF8, entity.getContentType(), "Content type should be given content type and charset");
        assertNull(entity.getContentEncoding(), "Content encoding should not be given");
        assertEquals(TEST_MESSAGE_WITH_SPECIAL_CHARACTERS,
                new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8),
                "Reading entity content with intended charset should result in the original (readable) message");
    }

    @Test
    void createRequestEntityJsonUtf8ThroughCharset() throws CamelExchangeException, IOException {
        HttpEndpoint httpEndpoint = mock(HttpEndpoint.class);
        HttpProducer httpProducer = new HttpProducer(httpEndpoint);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(TEST_MESSAGE_WITH_SPECIAL_CHARACTERS);
        when(message.getHeader(Exchange.CONTENT_TYPE, String.class)).thenReturn(APPLICATION_JSON.getMimeType());
        when(message.getHeader(CHARSET_NAME, String.class)).thenReturn(StandardCharsets.UTF_8.name());

        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);

        HttpEntity requestEntity = httpProducer.createRequestEntity(exchange);

        assertTrue(requestEntity instanceof StringEntity);
        StringEntity entity = (StringEntity) requestEntity;
        assertEquals(APPLICATION_JSON_UTF8, entity.getContentType(), "Content type should be given content type and charset");
        assertNull(entity.getContentEncoding(), "Content encoding should not be given");
        assertEquals(TEST_MESSAGE_WITH_SPECIAL_CHARACTERS,
                new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8),
                "Reading entity content with intended charset should result in the original (readable) message");
    }

}
