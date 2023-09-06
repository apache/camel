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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhatsAppWebhookProcessor extends AsyncProcessorSupport implements AsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookProcessor.class);

    private static final String MODE_QUERY_PARAM = "hub.mode";
    private static final String VERIFY_TOKEN_QUERY_PARAM = "hub.verify_token";
    private static final String CHALLENGE_QUERY_PARAM = "hub.challenge";

    private final WhatsAppConfiguration configuration;

    private AsyncProcessor next;

    public WhatsAppWebhookProcessor(Processor next, WhatsAppConfiguration configuration) {
        this.next = AsyncProcessorConverterHelper.convert(next);
        this.configuration = configuration;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String content;
        AtomicBoolean isGet = new AtomicBoolean(false);

        if ("GET".equalsIgnoreCase(exchange.getIn().getHeader(Exchange.HTTP_METHOD).toString())) {
            isGet.set(true);
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
            isGet.set(false);
            InputStream body = exchange.getIn().getBody(InputStream.class);

            try {
                content = new String(body.readAllBytes());
            } catch (IOException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        exchange.getMessage().setBody(content);

        return next.process(exchange, doneSync -> {
            exchange.getMessage().setBody(content);

            callback.done(doneSync);
        });
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
