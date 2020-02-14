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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration class for the webhook component.
 */
@UriParams
public class WebhookConfiguration implements Cloneable {

    /*
     * Note: all properties start with the 'webhook' prefix to avoid collision with the delegate endpoint.
     */

    @UriParam(label = "common")
    private String webhookComponentName;

    @UriParam(label = "common")
    private String webhookExternalUrl;

    @UriParam(label = "common")
    private String webhookBasePath;

    @UriParam(label = "common")
    private String webhookPath;

    @UriParam(label = "common", defaultValue = "true")
    private boolean webhookAutoRegister = true;

    @UriPath
    private String endpointUri;

    public WebhookConfiguration() {
    }

    public String getWebhookComponentName() {
        return webhookComponentName;
    }

    /**
     * Returns a copy of this configuration
     */
    public WebhookConfiguration copy() {
        try {
            return (WebhookConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    /**
     * The delegate uri. Must belong to a component that supports webhooks.
     */
    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    /**
     * The Camel Rest component to use for the REST transport, such as netty-http.
     */
    public void setWebhookComponentName(String webhookComponentName) {
        this.webhookComponentName = webhookComponentName;
    }

    public String getWebhookExternalUrl() {
        return webhookExternalUrl;
    }

    /**
     * The URL of the current service as seen by the webhook provider
     */
    public void setWebhookExternalUrl(String webhookExternalUrl) {
        this.webhookExternalUrl = webhookExternalUrl;
    }

    public String getWebhookBasePath() {
        return webhookBasePath;
    }

    /**
     * The first (base) path element where the webhook will be exposed.
     * It's a good practice to set it to a random string, so that it cannot be guessed by unauthorized parties.
     */
    public void setWebhookBasePath(String webhookBasePath) {
        this.webhookBasePath = webhookBasePath;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    /**
     * The path where the webhook endpoint will be exposed (relative to basePath, if any)
     */
    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public boolean isWebhookAutoRegister() {
        return webhookAutoRegister;
    }

    /**
     * Automatically register the webhook at startup and unregister it on shutdown.
     */
    public void setWebhookAutoRegister(boolean webhookAutoRegister) {
        this.webhookAutoRegister = webhookAutoRegister;
    }
}
