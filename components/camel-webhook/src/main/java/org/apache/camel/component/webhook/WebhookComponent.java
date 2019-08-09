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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * A Camel meta-component for exposing other components through webhooks.
 */
@Component("webhook")
public class WebhookComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private WebhookConfiguration configuration;

    public WebhookComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String delegateUri = remaining;
        if (ObjectHelper.isEmpty(delegateUri)) {
            throw new IllegalArgumentException("Wrong uri syntax : webhook:uri, got " + remaining);
        }

        WebhookConfiguration configuration = getConfiguration().copy();
        setProperties(configuration, parameters);

        RestConfiguration restConfig = getCamelContext().getRestConfiguration(configuration.getWebhookComponentName(), true);
        configuration.setRestConfiguration(restConfig);

        // we need to apply the params here
        if (parameters != null && !parameters.isEmpty()) {
            delegateUri = delegateUri + "?" + URISupport.createQueryString(parameters);
        }
        configuration.setEndpointUri(delegateUri);

        return new WebhookEndpoint(
                uri,
                this,
                configuration,
                delegateUri
        );
    }


    public WebhookConfiguration getConfiguration() {
        if (this.configuration == null) {
            this.configuration = new WebhookConfiguration();
        }
        return this.configuration;
    }

    /**
     * Set the default configuration for the webhook meta-component.
     */
    public void setConfiguration(WebhookConfiguration configuration) {
        this.configuration = configuration;
    }

}
