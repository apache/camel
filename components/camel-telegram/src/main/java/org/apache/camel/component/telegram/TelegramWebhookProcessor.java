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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.Update;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;

import static org.apache.camel.component.telegram.util.TelegramMessageHelper.populateExchange;

public class TelegramWebhookProcessor extends AsyncProcessorSupport implements AsyncProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AsyncProcessor next;

    public TelegramWebhookProcessor(Processor next) {
        this.next = AsyncProcessorConverterHelper.convert(next);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
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
