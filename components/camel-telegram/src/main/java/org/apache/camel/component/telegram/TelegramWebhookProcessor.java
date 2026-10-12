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
package org.apache.camel.component.telegram;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.Update;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.telegram.util.TelegramMessageHelper.populateExchange;

public class TelegramWebhookProcessor extends AsyncProcessorSupport {

    /**
     * The header in which Telegram sends the secret token the webhook was registered with.
     */
    public static final String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger LOG = LoggerFactory.getLogger(TelegramWebhookProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AsyncProcessor next;
    private final byte[] secretToken;

    public TelegramWebhookProcessor(Processor next, String secretToken) {
        this.next = AsyncProcessorConverterHelper.convert(next);
        this.secretToken = ObjectHelper.notNull(secretToken, "secretToken").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        String token = exchange.getIn().getHeader(SECRET_TOKEN_HEADER, String.class);
        if (token == null || !MessageDigest.isEqual(secretToken, token.getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("Rejecting a Telegram webhook request with a missing or invalid {} header."
                     + " A webhook registered outside Camel must use the webhookSecretToken as its secret_token.",
                    SECRET_TOKEN_HEADER);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
            exchange.getMessage().setBody("");
            callback.done(true);
            return true;
        }
        // the token has been checked, and is not passed on to the route
        exchange.getIn().removeHeader(SECRET_TOKEN_HEADER);

        // Let's process the body and transform it into a Telegram incoming message
        Update update;
        try (InputStream source = exchange.getIn().getBody(InputStream.class)) {
            update = MAPPER.readValue(source, Update.class);
        } catch (Exception ex) {
            exchange.setException(ex);
            callback.done(true);
            return true;
        }

        populateExchange(exchange, update);
        return next.process(exchange, doneSync -> {
            // No response data expected
            exchange.getMessage().setBody("");
            callback.done(doneSync);
        });
    }

}
