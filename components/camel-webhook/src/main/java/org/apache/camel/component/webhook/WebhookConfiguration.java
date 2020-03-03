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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * Configuration class for the webhook component.
 */
@UriParams
public class WebhookConfiguration implements Cloneable {

    private transient RestConfiguration restConfiguration;

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

    // cannot use getter/setter as its not a regular option
    public void storeConfiguration(RestConfiguration restConfiguration) {
        this.restConfiguration = restConfiguration;
    }

    // cannot use getter/setter as its not a regular option
    public RestConfiguration retrieveRestConfiguration() {
        return restConfiguration;
    }

    /**
     * Computes the external URL of the webhook as seen by the remote webhook provider.
     *
     * @return the webhook external URL
     */
    public String computeFullExternalUrl() throws UnknownHostException {
        String externalServerUrl = this.webhookExternalUrl;
        if (externalServerUrl == null) {
            externalServerUrl = computeServerUriPrefix();
        }
        String path = computeFullPath(true);
        return externalServerUrl + path;
    }

    /**
     * Computes the path part of the webhook.
     *
     * @param external indicates if it's the path seen by the external provider or the internal one.
     * @return the webhook full path
     */
    public String computeFullPath(boolean external) {
        // calculate the url to the rest service
        String path = webhookPath;
        if (path == null) {
            path = computeDefaultPath(endpointUri);
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

    /**
     * Computes the URL of the webhook that should be used to bind the REST endpoint locally.
     */
    public String computeServerUriPrefix() throws UnknownHostException {
        // if no explicit port/host configured, then use port from rest configuration
        String scheme = "http";
        String host = "";
        int port = 80;

        if (restConfiguration.getScheme() != null) {
            scheme = restConfiguration.getScheme();
        }
        if (restConfiguration.getHost() != null) {
            host = restConfiguration.getHost();
        }
        int num = restConfiguration.getPort();
        if (num > 0) {
            port = num;
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            if (restConfiguration.getHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                host = "0.0.0.0";
            } else if (restConfiguration.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (restConfiguration.getHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        return scheme + "://" + host + (port != 80 ? ":" + port : "");
    }

    /**
     * A default path is computed for the webhook if not provided by the user.
     * It uses a hash of the delegate endpoint in order for it to be reproducible.
     *
     * This is not random on purpose.
     */
    public static String computeDefaultPath(String uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(uri.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();

            return "/" + Base64.getUrlEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeCamelException("Cannot compute default webhook path", e);
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
