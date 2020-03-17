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

import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * A Camel meta-component for exposing other components through webhooks.
 */
@Component("webhook")
public class WebhookComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private WebhookConfiguration configuration = new WebhookConfiguration();

    public WebhookComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String delegateUri = remaining;
        if (ObjectHelper.isEmpty(delegateUri)) {
            throw new IllegalArgumentException("Wrong uri syntax : webhook:uri, got " + remaining);
        }

        WebhookConfiguration config = configuration != null ? configuration.copy() : new WebhookConfiguration();

        RestConfiguration restConfig = CamelContextHelper.getRestConfiguration(getCamelContext(), config.getWebhookComponentName());
        config.storeConfiguration(restConfig);

        WebhookEndpoint endpoint = new WebhookEndpoint(uri, this, config);
        setProperties(endpoint, parameters);
        // we need to apply the params here
        if (parameters != null && !parameters.isEmpty()) {
            delegateUri = delegateUri + "?" + resolveDelegateUriQuery(uri, parameters);
        }
        endpoint.getConfiguration().setEndpointUri(delegateUri);

        return endpoint;
    }

    private String resolveDelegateUriQuery(String uri, Map<String, Object> parameters) throws URISyntaxException {
        // parse parameters again from raw URI
        String query = uri.substring(uri.indexOf('?') + 1);
        Map<String, Object> rawParameters = URISupport.parseQuery(query, true);
        Map<String, Object> filtered = rawParameters.entrySet().stream()
                .filter(e -> parameters.containsKey(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return URISupport.createQueryString(filtered);
    }

    @Override
    public boolean useRawUri() {
        // disable URI encoding at webhook endpoint level to avoid encoding URI twice
        return true;
    }

    public WebhookConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * Set the default configuration for the webhook meta-component.
     */
    public void setConfiguration(WebhookConfiguration configuration) {
        this.configuration = configuration;
    }

}
