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

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookProcessorTest {

    private static final String VERIFY_TOKEN = "my token&more";
    private static final String SECRET = "my-app-secret";
    private static final String EVENT = "{\"object\":\"whatsapp_business_account\"}";

    private CamelContext context;
    private WhatsAppWebhookProcessor processor;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Object> routed = new AtomicReference<>();
    private final Processor route = exchange -> {
        calls.incrementAndGet();
        routed.set(exchange.getMessage().getBody());
    };

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        WhatsAppConfiguration configuration = new WhatsAppConfiguration();
        configuration.setWebhookVerifyToken(VERIFY_TOKEN);
        configuration.setWebhookSecret(SECRET);
        processor = new WhatsAppWebhookProcessor(route, configuration);
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
    void missingHttpMethodIsAnEvent() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(EVENT);
        exchange.getIn().setHeader("X-Hub-Signature-256", sign(EVENT, SECRET));

        process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(routed).hasValue(EVENT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void eventWithoutBody() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getIn().setHeader("X-Hub-Signature-256", sign("", SECRET));

        process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(routed).hasValue("");
        assertThat(calls).hasValue(1);
    }

    @Test
    void eventWithValidSignatureIsRouted() throws Exception {
        Exchange exchange = event(EVENT, sign(EVENT, SECRET));

        process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);
        assertThat(routed).hasValue(EVENT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void eventWithoutSignatureIsRejectedWith403AndNotRouted() {
        Exchange exchange = event(EVENT, null);

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(403);
        assertThat(exchange.getMessage().getBody()).isNull();
        assertThat(calls).hasValue(0);
    }

    @Test
    void eventWithSignatureOfAnotherSecretIsRejectedWith403AndNotRouted() throws Exception {
        Exchange exchange = event(EVENT, sign(EVENT, "another-secret"));

        process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(403);
        assertThat(calls).hasValue(0);
    }

    @Test
    void eventIsRejectedWith403WhenTheSecretIsMissing() {
        // the endpoint refuses to create the handler without a secret; the processor does not route either
        WhatsAppWebhookProcessor withoutSecret = new WhatsAppWebhookProcessor(route, new WhatsAppConfiguration());
        Exchange exchange = event(EVENT, "sha256=");

        withoutSecret.process(exchange, doneSync -> {
        });

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(403);
        assertThat(calls).hasValue(0);
    }

    @Test
    void eventIsRoutedWithoutSignatureWhenVerificationIsDisabled() throws Exception {
        WhatsAppEndpoint endpoint = context.getEndpoint(
                "whatsapp:123?authorizationToken=token&webhookVerification=false", WhatsAppEndpoint.class);
        Processor withoutVerification = endpoint.createWebhookHandler(route);
        Exchange exchange = event(EVENT, null);

        withoutVerification.process(exchange);

        assertThat(exchange.getException()).isNull();
        assertThat(routed).hasValue(EVENT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void eventReplyHasEmptyBody() throws Exception {
        WhatsAppWebhookProcessor replying = new WhatsAppWebhookProcessor(
                exchange -> exchange.getMessage().setBody("the result of the route"), configurationWithSecret());
        Exchange exchange = event(EVENT, sign(EVENT, SECRET));

        replying.process(exchange, doneSync -> {
        });

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(200);
        assertThat(exchange.getMessage().getBody()).isNull();
    }

    @Test
    void eventReplyKeepsTheResponseCodeOfTheRoute() throws Exception {
        WhatsAppWebhookProcessor replying = new WhatsAppWebhookProcessor(
                exchange -> exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202), configurationWithSecret());
        Exchange exchange = event(EVENT, sign(EVENT, SECRET));

        replying.process(exchange, doneSync -> {
        });

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE)).isEqualTo(202);
        assertThat(exchange.getMessage().getBody()).isNull();
    }

    private static WhatsAppConfiguration configurationWithSecret() {
        WhatsAppConfiguration configuration = new WhatsAppConfiguration();
        configuration.setWebhookSecret(SECRET);
        return configuration;
    }

    private Exchange event(String body, String signature) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getIn().setBody(body);
        if (signature != null) {
            exchange.getIn().setHeader("X-Hub-Signature-256", signature);
        }
        return exchange;
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
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
