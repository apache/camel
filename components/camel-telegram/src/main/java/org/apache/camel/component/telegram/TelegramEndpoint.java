/**
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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.telegram.model.Update;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The telegram component provides access to the <a href="https://core.telegram.org/bots/api">Telegram Bot API</a>.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "telegram", title = "Telegram", syntax = "telegram:type/authorizationToken", consumerClass = TelegramConsumer.class, label = "chat")
public class TelegramEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private TelegramConfiguration configuration;

    public TelegramEndpoint(String endpointUri, Component component, TelegramConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new TelegramProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        TelegramConsumer consumer = new TelegramConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Exchange createExchange(Update update) {
        Exchange exchange = super.createExchange();

        if (update.getMessage() != null) {
            exchange.getIn().setBody(update.getMessage());

            if (update.getMessage().getChat() != null) {
                exchange.getIn().setHeader(TelegramConstants.TELEGRAM_CHAT_ID, update.getMessage().getChat().getId());
            }
        }

        return exchange;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public TelegramConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(TelegramConfiguration configuration) {
        this.configuration = configuration;
    }

}
