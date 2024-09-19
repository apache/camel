package org.apache.camel.component.clickup;

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

import java.net.http.HttpClient;
import java.util.List;

/**
 * Receives events from ClickUp webhooks.
 */
@UriEndpoint(firstVersion = "4.9.0-SNAPSHOT", scheme = "clickup", title = "ClickUp", syntax = "clickup:workspaceId", category = {Category.CLOUD})
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

    public ClickUpEndpoint(
            String uri,
            ClickUpComponent component,
            ClickUpConfiguration configuration
    ) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() {
        throw new UnsupportedOperationException("producer not supported");
    }

    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("consumer not supported");
    }

    @Override
    public Processor createWebhookHandler(Processor next) {
        this.initClickUpWebhookService();

        this.clickUpWebhookProcessor = new ClickUpWebhookProcessor(next, this.clickUpWebhookService, this.configuration.getWebhookSecret());

        return clickUpWebhookProcessor;
    }

    @Override
    public void registerWebhook() throws Exception {
        Long workspaceId = this.configuration.getWorkspaceId();
        String webhookExternalUrl = this.webhookConfiguration.computeFullExternalUrl();

        this.registeredWebhook = this.clickUpWebhookService.registerWebhook(workspaceId, webhookExternalUrl, this.configuration.getEvents());

        this.clickUpWebhookProcessor.setWebhookSecret(this.registeredWebhook.getSecret());

        LOG.info("Webhook registered for workspace {} at the url {} with the following id {}.", workspaceId, webhookExternalUrl, this.registeredWebhook.getId());
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
                this.configuration.getAuthorizationToken()
        );

        // TODO: refactor - better encapsulate API client (ClickUpService) and higher-level service such as ClickUpWebhookService
        this.clickUpWebhookService = new ClickUpWebhookService(this.clickUpService);
    }

}
