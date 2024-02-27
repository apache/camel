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

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.service.TelegramServiceRestBotAPIAdapter;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive messages using the <a href="https://core.telegram.org/bots/api">Telegram Bot API</a>.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "telegram", title = "Telegram", syntax = "telegram:type",
             category = { Category.CLOUD, Category.API, Category.CHAT }, headersClass = TelegramConstants.class)
public class TelegramEndpoint extends ScheduledPollEndpoint implements WebhookCapableEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramEndpoint.class);

    @UriParam
    private TelegramConfiguration configuration;
    @UriParam(label = "advanced")
    private HttpClient client;
    @UriParam(label = "advanced", defaultValue = "" + (1024 * 1024))
    private int bufferSize = 1024 * 1024;

    private WebhookConfiguration webhookConfiguration;

    private TelegramService telegramService;

    public TelegramEndpoint(
                            String endpointUri,
                            Component component,
                            TelegramConfiguration configuration,
                            HttpClient client) {
        super(endpointUri, component);
        this.configuration = configuration;
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            HttpClient.Builder builder = HttpClient.newBuilder();

            if (configuration != null && ObjectHelper.isNotEmpty(configuration.getProxyHost())
                    && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
                LOG.debug("Setup {} proxy host:{} port:{} for TelegramService",
                        configuration.getProxyType(),
                        configuration.getProxyHost(),
                        configuration.getProxyPort());

                builder.proxy(
                        ProxySelector.of(new InetSocketAddress(configuration.getProxyHost(), configuration.getProxyPort())));
            }

            client = builder.build();
        }
        if (telegramService == null) {
            telegramService = new TelegramServiceRestBotAPIAdapter(
                    client,
                    configuration.getBaseUri(),
                    configuration.getAuthorizationToken(), bufferSize);
        }
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

    @Override
    public Processor createWebhookHandler(Processor next) {
        return new TelegramWebhookProcessor(next);
    }

    @Override
    public void registerWebhook() throws Exception {
        if (!telegramService.setWebhook(webhookConfiguration.computeFullExternalUrl())) {
            throw new RuntimeCamelException("The Telegram API refused to register a webhook");
        }
    }

    @Override
    public void unregisterWebhook() throws Exception {
        if (!telegramService.removeWebhook()) {
            throw new RuntimeCamelException("The Telegram API refused to unregister the webhook");
        }
    }

    public WebhookConfiguration getWebhookConfiguration() {
        return webhookConfiguration;
    }

    @Override
    public void setWebhookConfiguration(WebhookConfiguration webhookConfiguration) {
        this.webhookConfiguration = webhookConfiguration;
    }

    @Override
    public List<String> getWebhookMethods() {
        return Collections.singletonList("POST");
    }

    public TelegramConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(TelegramConfiguration configuration) {
        this.configuration = configuration;
    }

    public TelegramService getTelegramService() {
        return telegramService;
    }

    public HttpClient getClient() {
        return client;
    }

    /**
     * To use a custom {@link HttpClient}
     */
    public void setClient(HttpClient client) {
        this.client = client;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * The initial in-memory buffer size used when transferring data between Camel and AHC Client.
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
