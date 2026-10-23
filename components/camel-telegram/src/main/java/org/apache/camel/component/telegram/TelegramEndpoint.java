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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.service.TelegramServiceRestBotAPIAdapter;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.spi.EndpointServiceLocation;
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
public class TelegramEndpoint extends ScheduledPollEndpoint implements WebhookCapableEndpoint, EndpointServiceLocation {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramEndpoint.class);
    private static final Pattern WEBHOOK_SECRET_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    @UriParam
    private TelegramConfiguration configuration;
    @UriParam(label = "advanced")
    private HttpClient client;
    @UriParam(label = "advanced", defaultValue = "" + (8 * 1024))
    private int bufferSize = 8 * 1024;

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
    public String getServiceUrl() {
        if (configuration.getBaseUri() != null) {
            return configuration.getBaseUri();
        }
        return "https://api.telegram.org";
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
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
        if (!configuration.isWebhookSecretTokenEnabled()) {
            LOG.warn("The webhook secret token is disabled (webhookSecretTokenEnabled=false): webhook requests to {} are not"
                     + " verified",
                    this);
            return new TelegramWebhookProcessor(next, null);
        }
        return new TelegramWebhookProcessor(next, resolveWebhookSecretToken());
    }

    @Override
    public void registerWebhook() throws Exception {
        String url = webhookConfiguration.computeFullExternalUrl();
        boolean registered = configuration.isWebhookSecretTokenEnabled()
                ? telegramService.setWebhook(url, resolveWebhookSecretToken())
                : telegramService.setWebhook(url);
        if (!registered) {
            throw new RuntimeCamelException("The Telegram API refused to register a webhook");
        }
    }

    /**
     * Returns the secret token of the webhook: the configured one, or else one derived from the authorization token,
     * which stays the same across restarts and replicas.
     */
    String resolveWebhookSecretToken() {
        String token = configuration.getWebhookSecretToken();
        if (token == null) {
            return deriveWebhookSecretToken(configuration.getAuthorizationToken());
        }
        if (!WEBHOOK_SECRET_TOKEN_PATTERN.matcher(token).matches()) {
            throw new IllegalArgumentException(
                    "The webhookSecretToken must be 1 to 256 characters long, using only A-Z, a-z, 0-9, _ and -");
        }
        return token;
    }

    static String deriveWebhookSecretToken(String authorizationToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // the authorization token (the secret) is the key, and a fixed label is the message: the derived token
            // cannot be computed without the authorization token, and changes only when the authorization token does
            mac.init(new SecretKeySpec(authorizationToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal("camel-telegram-webhook-secret-token".getBytes(StandardCharsets.UTF_8));
            // base64url without padding only uses the characters that Telegram allows in a secret token
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new RuntimeCamelException("Cannot derive the webhook secret token", e);
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
     * The initial size, in bytes, of the in-memory buffer for the body of a request sent to Telegram. The buffer grows
     * to the size of the content, so it does not limit the size of the media that can be sent.
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
