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

import java.net.http.HttpClient;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.service.ClickUpService;
import org.apache.camel.component.clickup.service.ClickUpServiceApiImpl;
import org.apache.camel.component.clickup.service.ClickUpWebhookService;
import org.apache.camel.component.webhook.WebhookCapableEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives events from ClickUp
 */
@UriEndpoint(firstVersion = "4.9.0",
             scheme = "clickup",
             title = "ClickUp",
             syntax = "clickup:workspaceId",
             category = { Category.CLOUD },
             consumerOnly = true)
public class ClickUpEndpoint extends DefaultEndpoint implements WebhookCapableEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(ClickUpEndpoint.class);

    public static final String API_BASE_URL = "https://api.clickup.com/api/v2";

    @UriParam
    private final ClickUpConfiguration configuration;

    private ClickUpService clickUpService;
    private ClickUpWebhookService clickUpWebhookService;
    private WebhookConfiguration webhookConfiguration;
    private Webhook registeredWebhook;
    private ClickUpWebhookProcessor clickUpWebhookProcessor;

    public ClickUpEndpoint(String uri,
                           ClickUpComponent component,
                           ClickUpConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() {
        throw new UnsupportedOperationException("producer not supported");
    }

    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Use webhook component to wrap this endpoint");
    }

    @Override
    public Processor createWebhookHandler(Processor next) {
        this.initClickUpWebhookService();

        this.clickUpWebhookProcessor
                = new ClickUpWebhookProcessor(next, this.clickUpWebhookService, this.configuration.getWebhookSecret());

        return clickUpWebhookProcessor;
    }

    @Override
    public void registerWebhook() throws Exception {
        Long workspaceId = this.configuration.getWorkspaceId();
        String webhookExternalUrl = this.webhookConfiguration.computeFullExternalUrl();

        this.registeredWebhook
                = this.clickUpWebhookService.registerWebhook(workspaceId, webhookExternalUrl, this.configuration.getEvents());

        this.clickUpWebhookProcessor.setWebhookSecret(this.registeredWebhook.getSecret());

        LOG.info("Webhook registered for workspace {} at the url {} with the following id {}.", workspaceId, webhookExternalUrl,
                this.registeredWebhook.getId());
    }

    @Override
    public void unregisterWebhook() {
        this.clickUpService.deleteWebhook(this.registeredWebhook.getId());

        LOG.info("Webhook {} unregistered", this.registeredWebhook.getId());
    }

    @Override
    public void setWebhookConfiguration(WebhookConfiguration webhookConfiguration) {
        this.webhookConfiguration = webhookConfiguration;
    }

    @Override
    public List<String> getWebhookMethods() {
        return List.of("POST");
    }

    public ClickUpConfiguration getConfiguration() {
        return configuration;
    }

    private void initClickUpWebhookService() {
        HttpClient httpClient = HttpClient.newBuilder().build();

        this.clickUpService = new ClickUpServiceApiImpl(
                httpClient,
                this.configuration.getBaseUrl() != null ? this.configuration.getBaseUrl() : API_BASE_URL,
                this.configuration.getAuthorizationToken());

        // TODO: refactor - better encapsulate API client (ClickUpService) and higher-level service such as ClickUpWebhookService
        this.clickUpWebhookService = new ClickUpWebhookService(this.clickUpService);
    }

}
