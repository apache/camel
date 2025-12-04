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

package org.apache.camel.component.clickup;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.clickup.model.Event;
import org.apache.camel.component.clickup.service.ClickUpWebhookService;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;

public class ClickUpWebhookProcessor extends AsyncProcessorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsyncProcessor nextProcessor;

    private final ClickUpWebhookService clickUpWebhookService;

    private String webhookSecret;

    public ClickUpWebhookProcessor(
            Processor nextProcessor, ClickUpWebhookService clickUpWebhookService, String webhookSecret) {
        this.nextProcessor = AsyncProcessorConverterHelper.convert(nextProcessor);
        this.clickUpWebhookService = clickUpWebhookService;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message incomingMessage = exchange.getIn();

        try {
            this.clickUpWebhookService.validateMessageSignature(incomingMessage, this.webhookSecret);
        } catch (RuntimeException e) {
            exchange.setException(e);

            callback.done(true);

            return true;
        }

        Event event;
        try (InputStream body = exchange.getIn().getBody(InputStream.class)) {
            event = MAPPER.readValue(body, Event.class);
        } catch (IOException e) {
            exchange.setException(e);

            callback.done(true);

            return true;
        }

        exchange.getMessage().setBody(event);

        // This is needed to adhere to the delegate pattern adopted by the camel-webhook meta-component
        return nextProcessor.process(exchange, doneSync -> {
            exchange.getMessage().setBody(null);

            callback.done(doneSync);
        });
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
