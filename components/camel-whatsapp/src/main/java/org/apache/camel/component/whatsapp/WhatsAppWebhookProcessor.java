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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhatsAppWebhookProcessor extends AsyncProcessorSupport {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookProcessor.class);

    private static final String MODE_QUERY_PARAM = "hub.mode";
    private static final String VERIFY_TOKEN_QUERY_PARAM = "hub.verify_token";
    private static final String CHALLENGE_QUERY_PARAM = "hub.challenge";
    private static final String HUB_SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WhatsAppConfiguration configuration;

    private AsyncProcessor next;

    public WhatsAppWebhookProcessor(Processor next, WhatsAppConfiguration configuration) {
        this.next = AsyncProcessorConverterHelper.convert(next);
        this.configuration = configuration;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String content;

        if ("GET".equalsIgnoreCase(exchange.getIn().getHeader(Exchange.HTTP_METHOD).toString())) {
            // Parse params from the webhook verification request
            Map<String, String> queryParams = parseQueryParam(exchange);

            String mode = queryParams.get(MODE_QUERY_PARAM);
            String token = queryParams.get(VERIFY_TOKEN_QUERY_PARAM);
            String challenge = queryParams.get(CHALLENGE_QUERY_PARAM);

            if (mode != null && token != null) {
                if ("subscribe".equals(mode) && token.equals(configuration.getWebhookVerifyToken())) {
                    LOG.info("WhatsApp Webhook verified and subscribed");
                    content = challenge;
                } else {
                    content = null;
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                }
            } else {
                LOG.error("{} or {} missing from request query param", MODE_QUERY_PARAM, VERIFY_TOKEN_QUERY_PARAM);
                content = null;
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            }
        } else {
            byte[] requestBody;
            try (InputStream body = exchange.getIn().getBody(InputStream.class)) {
                requestBody = body.readAllBytes();
            } catch (IOException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }

            // When a webhook secret is configured, verify the X-Hub-Signature-256 payload signature
            String webhookSecret = configuration.getWebhookSecret();
            if (webhookSecret != null && !isValidSignature(requestBody,
                    exchange.getIn().getHeader(HUB_SIGNATURE_HEADER, String.class), webhookSecret)) {
                LOG.warn("Rejecting WhatsApp webhook event with missing or invalid {} signature", HUB_SIGNATURE_HEADER);
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                callback.done(true);
                return true;
            }

            content = new String(requestBody, StandardCharsets.UTF_8);
        }

        exchange.getMessage().setBody(content);

        return next.process(exchange, doneSync -> {
            exchange.getMessage().setBody(content);

            callback.done(doneSync);
        });
    }

    /**
     * Verifies the {@code X-Hub-Signature-256} header against an HMAC-SHA256 of the raw request body keyed by the
     * configured webhook secret, using a constant-time comparison.
     */
    static boolean isValidSignature(byte[] payload, String signatureHeader, String secret) {
        if (signatureHeader == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private Map<String, String> parseQueryParam(Exchange exchange) {
        Map<String, String> queryParams = new HashMap<>();

        if (exchange.getIn().getHeader(Exchange.HTTP_QUERY) != null) {
            String[] pairs = exchange.getIn().getHeader(Exchange.HTTP_QUERY).toString().split("&");
            for (String pair : pairs) {
                String[] keyValuePair = pair.split("=");

                queryParams.put(keyValuePair[0], keyValuePair[1]);
            }
        }

        return queryParams;
    }

}
