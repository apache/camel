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

    @UriPath(description = "The URI of the delegate endpoint, of a component that supports webhooks, for example"
                           + " telegram:bots, clickup:workspaceId or whatsapp:phoneNumberId. The query parameters of the"
                           + " webhook endpoint that are not webhook options are passed on to the delegate endpoint.")
    @Metadata(required = true)
    private String endpointUri;

    @UriParam(description = "The name of the REST component that exposes the HTTP endpoint of the webhook, such as"
                            + " platform-http or netty-http. It names a bean in the registry or a component, which must be"
                            + " a RestConsumerFactory. If not set, a component of the CamelContext that is a"
                            + " RestConsumerFactory is used, otherwise the only RestConsumerFactory bean in the registry,"
                            + " otherwise the only one of coap, netty-http, jetty, servlet and undertow on the classpath.")
    private String webhookComponentName;

    @UriParam(description = "The URL at which the webhook provider reaches this service, for example"
                            + " https://camel.example.com, without a trailing slash. The delegate component registers this"
                            + " URL followed by the path of the webhook (the context path of the REST configuration,"
                            + " webhookBasePath and webhookPath) at the provider. If not set, the URL is made from the"
                            + " scheme, host and port of the REST configuration. When that has no host, its hostNameResolver"
                            + " picks one, which is 0.0.0.0 by default and which the provider cannot reach.")
    private String webhookExternalUrl;

    @UriParam(description = "A path put in front of webhookPath in the path of the webhook, for example a random value"
                            + " that is hard to guess, so that only the provider knows the URL. A leading slash is added"
                            + " when missing. If not set, the path of the webhook is webhookPath alone.")
    private String webhookBasePath;

    @UriParam(description = "The path of the webhook, after webhookBasePath. A leading slash is added when missing. If not"
                            + " set, the path is the SHA-256 hash of the delegate endpoint URI in URL-safe Base64, which"
                            + " stays the same across restarts. The WhatsApp component replaces it with its own webhookPath"
                            + " option.")
    private String webhookPath;

    @UriParam(defaultValue = "true",
              description = "Whether the delegate component registers the URL of the webhook at the provider when the"
                            + " endpoint starts, and unregisters it when the endpoint stops, for example with the setWebhook"
                            + " and deleteWebhook calls of the Telegram Bot API. Set it to false when the webhook is"
                            + " registered in another way, for example with a WebhookRoutePolicyFactory, which requires"
                            + " false. The WhatsApp component does not register the webhook.")
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
     * @param  external indicates if it's the path seen by the external provider or the internal one.
     * @return          the webhook full path
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
     * A default path is computed for the webhook if not provided by the user. It uses a hash of the delegate endpoint
     * in order for it to be reproducible.
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

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public void setWebhookComponentName(String webhookComponentName) {
        this.webhookComponentName = webhookComponentName;
    }

    public String getWebhookExternalUrl() {
        return webhookExternalUrl;
    }

    public void setWebhookExternalUrl(String webhookExternalUrl) {
        this.webhookExternalUrl = webhookExternalUrl;
    }

    public String getWebhookBasePath() {
        return webhookBasePath;
    }

    public void setWebhookBasePath(String webhookBasePath) {
        this.webhookBasePath = webhookBasePath;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public boolean isWebhookAutoRegister() {
        return webhookAutoRegister;
    }

    public void setWebhookAutoRegister(boolean webhookAutoRegister) {
        this.webhookAutoRegister = webhookAutoRegister;
    }
}
