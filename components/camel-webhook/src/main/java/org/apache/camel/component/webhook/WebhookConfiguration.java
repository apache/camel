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

import java.net.UnknownHostException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.webhook.WebhookComponent.computeServerUriPrefix;

/**
 * Configuration class for the webhook component.
 */
@UriParams
public class WebhookConfiguration implements Cloneable {

    /*
     * Note: all properties start with the 'webhook' prefix to avoid collision with the delegate endpoint.
     */

    @UriPath @Metadata(required = true)
    private String endpointUri;

    @UriParam
    private String webhookComponentName;

    @UriParam
    private String webhookExternalUrl;

    @UriParam
    private String webhookBasePath;

    @UriParam
    private String webhookPath;

    @UriParam(defaultValue = "true")
    private boolean webhookAutoRegister = true;

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

    /**
     * Computes the external URL of the webhook as seen by the remote webhook provider.
     *
     * @param restConfiguration rest configuration
     * @return the webhook external URL
     */
    public String computeFullExternalUrl(RestConfiguration restConfiguration) throws UnknownHostException {
        String externalServerUrl = this.webhookExternalUrl;
        if (externalServerUrl == null) {
            externalServerUrl = computeServerUriPrefix(restConfiguration);
        }
        String path = computeFullPath(restConfiguration, true);
        return externalServerUrl + path;
    }

    /**
     * Computes the path part of the webhook.
     *
     * @param restConfiguration rest configuration
     * @param external indicates if it's the path seen by the external provider or the internal one.
     * @return the webhook full path
     */
    public String computeFullPath(RestConfiguration restConfiguration, boolean external) {
        // calculate the url to the rest service
        String path = webhookPath;
        if (path == null) {
            path = WebhookComponent.computeDefaultPath(endpointUri);
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (webhookBasePath != null) {
            if (!webhookBasePath.startsWith("/")) {
                path = "/" + webhookBasePath + path;
            } else {
                path = webhookBasePath + path;
            }
        }

        if (external) {
            String contextPath = restConfiguration.getContextPath();
            if (contextPath != null) {
                if (!contextPath.startsWith("/")) {
                    path = "/" + contextPath + path;
                } else {
                    path = contextPath + path;
                }
            }
        }

        return path;
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
