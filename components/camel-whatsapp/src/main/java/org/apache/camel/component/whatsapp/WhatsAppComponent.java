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
package org.apache.camel.component.whatsapp;

import java.net.http.HttpClient;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

@Component("whatsapp")
public class WhatsAppComponent extends DefaultComponent {

    public static final String API_DEFAULT_URL = "https://graph.facebook.com";
    public static final String API_DEFAULT_VERSION = "v13.0";

    @Metadata(required = true, description = "Phone Number ID taken from WhatsApp Meta for Developers Dashboard")
    private String phoneNumberId;
    @Metadata(label = "security", secret = true, required = true,
              description = "Authorization Token taken from WhatsApp Meta for Developers Dashboard")
    private String authorizationToken;

    @Metadata(label = "advanced", description = "Java 11 HttpClient implementation")
    private HttpClient client;
    @Metadata(label = "advanced", defaultValue = API_DEFAULT_URL,
              description = "Can be used to set an alternative base URI, e.g. when you want to test the component against a mock WhatsApp API")
    private String baseUri = API_DEFAULT_URL;
    @Metadata(label = "advanced", defaultValue = API_DEFAULT_VERSION, description = "WhatsApp Cloud API version")
    private String apiVersion = API_DEFAULT_VERSION;
    @Metadata(description = "Webhook verify token", label = "advanced", secret = true)
    private String webhookVerifyToken;

    public WhatsAppComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WhatsAppConfiguration configuration = new WhatsAppConfiguration();

        if (configuration.getBaseUri() == null) {
            configuration.setBaseUri(baseUri);
        }
        if (configuration.getApiVersion() == null) {
            configuration.setApiVersion(apiVersion);
        }
        if (configuration.getWebhookVerifyToken() == null) {
            configuration.setWebhookVerifyToken(webhookVerifyToken);
        }

        configuration.setAuthorizationToken(authorizationToken);

        if (remaining.endsWith("/")) {
            remaining = remaining.substring(0, remaining.length() - 1);
        }
        configuration.setPhoneNumberId(remaining);

        WhatsAppEndpoint endpoint = new WhatsAppEndpoint(uri, this, configuration, client);

        setProperties(endpoint, parameters);

        if (ObjectHelper.isEmpty(endpoint.getConfiguration().getAuthorizationToken())) {
            throw new IllegalArgumentException(
                    "AuthorizationToken must be configured on either component or endpoint for whatsapp: " + uri);
        }

        return endpoint;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

    public HttpClient getClient() {
        return client;
    }

    public void setClient(HttpClient client) {
        this.client = client;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }
}
