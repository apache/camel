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

import java.util.Collections;
import java.util.List;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.model.Update;
import org.apache.camel.component.telegram.service.TelegramServiceRestBotAPIAdapter;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.proxy.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.telegram.util.TelegramMessageHelper.populateExchange;

/**
 * The telegram component provides access to the <a href="https://core.telegram.org/bots/api">Telegram Bot API</a>.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "telegram", title = "Telegram", syntax = "telegram:type", label = "chat")
public class TelegramEndpoint extends ScheduledPollEndpoint implements WebhookCapableEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramEndpoint.class);

    @UriParam
    private TelegramConfiguration configuration;
    @UriParam(label = "advanced")
    private AsyncHttpClientConfig clientConfig;
    @UriParam(label = "advanced", defaultValue = "" + (4 * 1024))
    private int bufferSize = 4 * 1024;

    private WebhookConfiguration webhookConfiguration;

    private AsyncHttpClient client;
    private TelegramService telegramService;

    public TelegramEndpoint(
            String endpointUri,
            Component component,
            TelegramConfiguration configuration,
            AsyncHttpClient client,
            AsyncHttpClientConfig clientConfig) {
        super(endpointUri, component);
        this.configuration = configuration;
        this.client = client;
        this.clientConfig = clientConfig;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            DefaultAsyncHttpClientConfig.Builder builder = clientConfig != null
                    ? new DefaultAsyncHttpClientConfig.Builder(clientConfig)
                    : new DefaultAsyncHttpClientConfig.Builder();

            if (configuration != null && ObjectHelper.isNotEmpty(configuration.getProxyHost())
                    && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
                LOG.debug("Setup http proxy host:{} port:{} for TelegramService", configuration.getProxyHost(),
                        configuration.getProxyPort());
                builder.setProxyServer(
                        new ProxyServer.Builder(configuration.getProxyHost(), configuration.getProxyPort()).build());
            }
            final AsyncHttpClientConfig config = builder.build();
            client = new DefaultAsyncHttpClient(config);
        }
        if (telegramService == null) {
            telegramService = new TelegramServiceRestBotAPIAdapter(
                    client,
                    bufferSize,
                    configuration.getBaseUri(),
                    configuration.getAuthorizationToken());
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // ensure client is closed when stopping
        if (client != null && !client.isClosed()) {
            client.close();
        }
        client = null;
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
        populateExchange(exchange, update);
        return exchange;
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

    public AsyncHttpClient getClient() {
        return client;
    }

    /**
     * To use a custom {@link AsyncHttpClient}
     */
    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * To configure the AsyncHttpClient to use a custom com.ning.http.client.AsyncHttpClientConfig instance.
     */
    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
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
