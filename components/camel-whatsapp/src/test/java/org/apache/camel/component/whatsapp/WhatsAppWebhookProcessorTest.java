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
package org.apache.camel.component.whatsapp;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookProcessorTest {

    private static final String VERIFY_TOKEN = "my token&more";

    private CamelContext context;
    private WhatsAppWebhookProcessor processor;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        WhatsAppConfiguration configuration = new WhatsAppConfiguration();
        configuration.setWebhookVerifyToken(VERIFY_TOKEN);
        processor = new WhatsAppWebhookProcessor(exchange -> calls.incrementAndGet(), configuration);
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void emptyQueryValueAndMissingEqualsSign() {
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY, "hub.challenge=&hub.mode=subscribe&flag");

        assertThat(WhatsAppWebhookProcessor.parseQueryParam(exchange))
                .containsExactlyInAnyOrderEntriesOf(Map.of("hub.challenge", "", "hub.mode", "subscribe", "flag", ""));

        process(exchange);
        // hub.verify_token is missing
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
    }

    @Test
    void verifiedWithEmptyChallenge() {
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY,
                "hub.mode=subscribe&hub.verify_token=my%20token%26more&hub.challenge=");

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("");
        assertThat(calls).hasValue(1);
    }

    @Test
    void encodedTokenAndChallengeAreDecoded() {
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY,
                "hub.mode=subscribe&hub.verify_token=my+token%26more&hub.challenge=a%3Db%20c");

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("a=b c");
        assertThat(calls).hasValue(1);
    }

    @Test
    void rawQueryIsPreferred() {
        // netty-http sets the decoded query in CamelHttpQuery and the raw query in CamelHttpRawQuery
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY,
                "hub.mode=subscribe&hub.verify_token=my token&more&hub.challenge=100%");
        exchange.getIn().setHeader(Exchange.HTTP_RAW_QUERY,
                "hub.mode=subscribe&hub.verify_token=my%20token%26more&hub.challenge=100%25");

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("100%");
        assertThat(calls).hasValue(1);
    }

    @Test
    void wrongTokenIsRejected() {
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY,
                "hub.mode=subscribe&hub.verify_token=my%20token&hub.challenge=123");

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(403);
        assertThat(exchange.getMessage().getBody()).isNull();
    }

    @Test
    void invalidEncodingIsBadRequest() {
        Exchange exchange = verificationRequest(Exchange.HTTP_QUERY,
                "hub.mode=subscribe&hub.verify_token=%zz&hub.challenge=123");

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(400);
        assertThat(exchange.getException()).isNull();
        assertThat(calls).hasValue(0);
    }

    @Test
    void missingHttpMethodIsAnEvent() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("{\"object\":\"whatsapp_business_account\"}");

        process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("{\"object\":\"whatsapp_business_account\"}");
        assertThat(calls).hasValue(1);
    }

    @Test
    void eventWithoutBody() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");

        process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("");
        assertThat(calls).hasValue(1);
    }

    private Exchange verificationRequest(String header, String query) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
        exchange.getIn().setHeader(header, query);
        return exchange;
    }

    private void process(Exchange exchange) {
        processor.process(exchange, doneSync -> {
        });
    }
}
