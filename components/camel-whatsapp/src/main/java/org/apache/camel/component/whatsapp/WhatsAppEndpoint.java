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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.whatsapp.service.WhatsAppServiceRestAPIAdapter;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send messages to WhatsApp.
 */
@UriEndpoint(firstVersion = "3.19.0", scheme = "whatsapp", title = "WhatsApp", syntax = "whatsapp:phoneNumberId",
             producerOnly = true,
             category = {
                     Category.CLOUD, Category.API,
                     Category.CHAT },
             headersClass = WhatsAppConstants.class)
public class WhatsAppEndpoint extends ScheduledPollEndpoint implements WebhookCapableEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppEndpoint.class);

    @UriParam
    private WhatsAppConfiguration configuration;

    @UriParam(label = "advanced", description = "HttpClient implementation")
    private HttpClient httpClient;
    @UriParam(label = "advanced", description = "WhatsApp service implementation")
    private WhatsAppService whatsappService;

    private WebhookConfiguration webhookConfiguration;

    public WhatsAppEndpoint(String endpointUri, Component component, WhatsAppConfiguration configuration, HttpClient client) {
        super(endpointUri, component);
        this.configuration = configuration;
        this.httpClient = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (httpClient == null) {
            httpClient
                    = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build();
        }
        if (whatsappService == null) {
            whatsappService = new WhatsAppServiceRestAPIAdapter(
                    httpClient, configuration.getBaseUri(), configuration.getApiVersion(), configuration.getPhoneNumberId(),
                    configuration.getAuthorizationToken());
        }
        LOG.debug("client {}", httpClient);
        LOG.debug("whatsappService {}", whatsappService);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // ensure client is closed when stopping
        httpClient = null;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new WhatsAppProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("consumer not supported");
    }

    @Override
    public Processor createWebhookHandler(Processor next) {
        return new WhatsAppWebhookProcessor(next, configuration);
    }

    @Override
    public List<String> getWebhookMethods() {
        return List.of("POST", "GET");
    }

    @Override
    public void registerWebhook() throws Exception {
    }

    @Override
    public void setWebhookConfiguration(WebhookConfiguration webhookConfiguration) {
        webhookConfiguration.setWebhookPath(configuration.getWebhookPath());
        this.webhookConfiguration = webhookConfiguration;
    }

    @Override
    public void unregisterWebhook() throws Exception {
    }

    public WhatsAppConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(WhatsAppConfiguration configuration) {
        this.configuration = configuration;
    }

    public WhatsAppService getWhatsappService() {
        return whatsappService;
    }

    public void setWhatsappService(WhatsAppService whatsappService) {
        this.whatsappService = whatsappService;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public WebhookConfiguration getWebhookConfiguration() {
        return webhookConfiguration;
    }
}
