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
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Endpoint;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.HostUtils;
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
        RestConfiguration restConfig = getCamelContext().getRestConfiguration(config.getWebhookComponentName(), true);

        WebhookEndpoint endpoint = new WebhookEndpoint(uri, this, config, restConfig);
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

    /**
     * Computes the URL of the webhook that should be used to bind the REST endpoint locally.
     */
    public static String computeServerUriPrefix(RestConfiguration restConfiguration) throws UnknownHostException {
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

}
