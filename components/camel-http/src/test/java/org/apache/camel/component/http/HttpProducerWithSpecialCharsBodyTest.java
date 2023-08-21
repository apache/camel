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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpProducerWithSpecialCharsBodyTest {

    private static final String TEST_MESSAGE_WITH_SPECIAL_CHARACTERS = """
            {
                "description": "Example with special characters",
                "BasicLatin": "! # $ % & ' ( ) * + , - . / : ; < = > ?",
                "Latin1": "¡ ¿ © ® ñ ö ü",
                "Greek": "Α β γ Δ Ω",
                "Cyrillic": "А б в Г д",
                "Hebrew": "א ב ג ד ה",
                "Arabic": "ا ب ت ث ج",
                "Devanagari": "अ आ इ ई उ",
                "CJK Unified Ideographs" : "一 二 三 四 五",
                "Emoticons": "😀 😎 😊 🌍"
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
        assertEquals(StandardCharsets.UTF_8.name(), entity.getContentEncoding(), "Content encoding should be given charset");
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
        assertEquals(StandardCharsets.UTF_8.name(), entity.getContentEncoding(), "Content encoding should be given charset");
        assertEquals(TEST_MESSAGE_WITH_SPECIAL_CHARACTERS,
                new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8),
                "Reading entity content with intended charset should result in the original (readable) message");
    }

}
