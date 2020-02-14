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
package org.apache.camel.component.webhook;

import java.util.function.Supplier;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.function.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The webhook component allows other Camel components that can receive push notifications to expose
 * webhook endpoints and automatically register them with their own webhook provider.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "webhook", title = "Webhook", syntax = "webhook:endpointUri", consumerOnly = true, label = "cloud", lenientProperties = true)
public class WebhookEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookEndpoint.class);

    private final Supplier<WebhookCapableEndpoint> delegateEndpoint;

    @UriParam(label = "advanced")
    private WebhookConfiguration configuration;

    public WebhookEndpoint(String uri, WebhookComponent component, WebhookConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.delegateEndpoint = Suppliers.memorize(() -> {
            Endpoint delegate = getCamelContext().getEndpoint(configuration.getEndpointUri());
            if (!(delegate instanceof WebhookCapableEndpoint)) {
                throw new IllegalArgumentException("The provided endpoint is not capable of being used in webhook mode: " + configuration.getEndpointUri());
            }

            WebhookCapableEndpoint delegateEndpoint = (WebhookCapableEndpoint) delegate;
            delegateEndpoint.setWebhookConfiguration(configuration);

            return delegateEndpoint;
        });
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException("You cannot create a producer with the webhook endpoint.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RestConsumerFactory factory = WebhookUtils.locateRestConsumerFactory(getCamelContext(), configuration);

        String path = WebhookUtils.computeFullPath(getCamelContext(), configuration, false);
        String serverUrl = WebhookUtils.computeServerUriPrefix(getCamelContext(), configuration);
        String url = serverUrl + path;
        RestConfiguration restConfiguration = WebhookUtils.getRestConfiguration(getCamelContext(), configuration);

        Processor handler = delegateEndpoint.get().createWebhookHandler(processor);

        return new MultiRestConsumer(getCamelContext(), factory, this, handler,  delegateEndpoint.get().getWebhookMethods(), url, path,
            restConfiguration, this::configureConsumer);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.isWebhookAutoRegister()) {
            LOG.info("Registering webhook for endpoint: {}", delegateEndpoint);
            delegateEndpoint.get().registerWebhook();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (configuration.isWebhookAutoRegister() && delegateEndpoint != null) {
            LOG.info("Unregistering webhook for endpoint: {}", delegateEndpoint);
            delegateEndpoint.get().unregisterWebhook();
        }
    }

    public WebhookConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WebhookCapableEndpoint getEndpoint() {
        return  delegateEndpoint.get();
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }
}
