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
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Some utilities for the webhook component.
 */
public final class WebhookUtils {

    private static final String[] DEFAULT_REST_CONSUMER_COMPONENTS = new String[]{"coap", "netty-http", "jetty", "servlet", "undertow"};

    private WebhookUtils() {
    }

    /**
     * Used to locate the most suitable {@code RestConsumerFactory}.
     */
    public static RestConsumerFactory locateRestConsumerFactory(CamelContext context, WebhookConfiguration configuration) {
        RestConsumerFactory factory = null;
        if (configuration.getWebhookComponentName() != null) {
            Object comp = context.getRegistry().lookupByName(configuration.getWebhookComponentName());
            if (comp instanceof RestConsumerFactory) {
                factory = (RestConsumerFactory) comp;
            } else {
                comp = context.getComponent(configuration.getWebhookComponentName());
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + configuration.getWebhookComponentName() + " is not a RestConsumerFactory");
                } else {
                    throw new NoSuchBeanException(configuration.getWebhookComponentName(), RestConsumerFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : context.getComponentNames()) {
                Component comp = context.getComponent(name);
                if (comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestConsumerFactory> factories = context.getRegistry().findByType(RestConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        // no explicit factory found then try to see if we can find any of the default rest consumer components
        // and there must only be exactly one so we safely can pick this one
        if (factory == null) {
            RestConsumerFactory found = null;
            for (String name : DEFAULT_REST_CONSUMER_COMPONENTS) {
                Object comp = context.getComponent(name, true);
                if (comp instanceof RestConsumerFactory) {
                    if (found == null) {
                        found = (RestConsumerFactory) comp;
                    } else {
                        throw new IllegalArgumentException("Multiple RestConsumerFactory found on classpath. Configure explicit which component to use");
                    }
                }
            }
            if (found != null) {
                factory = found;
            }
        }

        if (factory == null) {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }
        return factory;
    }



    /**
     * Computes the external URL of the webhook as seen by the remote webhook provider.
     */
    public static String computeFullExternalUrl(CamelContext camelContext, WebhookConfiguration configuration) throws UnknownHostException {
        String externalServerUrl = configuration.getWebhookExternalUrl();
        if (externalServerUrl == null) {
            externalServerUrl = computeServerUriPrefix(camelContext, configuration);
        }
        String path = computeFullPath(camelContext, configuration, true);
        return externalServerUrl + path;
    }

    /**
     * Computes the URL of the webhook that should be used to bind the REST endpoint locally.
     */
    public static String computeServerUriPrefix(CamelContext camelContext, WebhookConfiguration configuration) throws UnknownHostException {
        RestConfiguration restConfiguration = getRestConfiguration(camelContext, configuration);

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
     * Computes the path part of the webhook.
     *
     * @param external indicates if it's the path seen by the external provider or the internal one.
     * @return the webhook full path
     */
    public static String computeFullPath(CamelContext camelContext, WebhookConfiguration configuration, boolean external) {
        RestConfiguration restConfiguration = getRestConfiguration(camelContext, configuration);

        // calculate the url to the rest service
        String path = configuration.getWebhookPath();
        if (path == null) {
            path = computeDefaultPath(configuration.getEndpointUri());
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (configuration.getWebhookBasePath() != null) {
            if (!configuration.getWebhookBasePath().startsWith("/")) {
                path = "/" + configuration.getWebhookBasePath() + path;
            } else {
                path = configuration.getWebhookBasePath() + path;
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

    public static RestConfiguration getRestConfiguration(CamelContext camelContext, WebhookConfiguration configuration) {
        return camelContext.getRestConfiguration(configuration.getWebhookComponentName(), true);
    }

    public static String resolveDelegateUriQuery(String uri, Map<String, Object> parameters) throws URISyntaxException {
        // parse parameters again from raw URI
        String query = uri.substring(uri.indexOf('?') + 1);
        Map<String, Object> rawParameters = URISupport.parseQuery(query, true);
        Map<String, Object> filtered = rawParameters.entrySet().stream()
            .filter(e -> parameters.containsKey(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return URISupport.createQueryString(filtered);
    }

}
