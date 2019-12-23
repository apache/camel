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
package org.apache.camel.component.telegram;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;

@Component("telegram")
public class TelegramComponent extends DefaultComponent {
    public static final String BOT_API_DEFAULT_URL = "https://api.telegram.org";

    @Metadata(label = "security", secret = true)
    private String authorizationToken;

    @Metadata(label = "advanced")
    private AsyncHttpClient client;
    @Metadata(label = "advanced")
    private AsyncHttpClientConfig clientConfig;

    @Metadata(label = "advanced", defaultValue = BOT_API_DEFAULT_URL, description = "Can be used to set an alternative base URI, e.g. when you want to test the component against a mock Telegram API")
    private String baseUri = BOT_API_DEFAULT_URL;

    public TelegramComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        TelegramConfiguration configuration = new TelegramConfiguration();

        // ignore trailing slash
        if (remaining.endsWith("/")) {
            remaining = remaining.substring(0, remaining.length() - 1);
        }
        configuration.setType(remaining);

        if (!remaining.equals(TelegramConfiguration.ENDPOINT_TYPE_BOTS)) {
            throw new IllegalArgumentException("Unsupported endpoint type for uri " + uri + ", remaining " + remaining);
        }
        if (configuration.getBaseUri() == null) {
            configuration.setBaseUri(baseUri);
        }

        TelegramEndpoint endpoint = new TelegramEndpoint(uri, this, configuration, client, clientConfig);
        configuration.setAuthorizationToken(authorizationToken);
        setProperties(endpoint, parameters);

        if (endpoint.getConfiguration().getAuthorizationToken() == null) {
            throw new IllegalArgumentException("AuthorizationToken must be configured on either component or endpoint for telegram: " + uri);
        }

        return endpoint;
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    /**
     * The default Telegram authorization token to be used when the information is not provided in the endpoints.
     */
    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    /**
     * To use a custom {@link AsyncHttpClient}
     */
    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * To configure the AsyncHttpClient to use a custom com.ning.http.client.AsyncHttpClientConfig instance.
     */
    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public String getBaseUri() {
        return baseUri;
    }

    /**
     * Set an alternative base URI, e.g. when you want to test the component against a mock Telegram API.
     */
    public void setBaseUri(String telegramBaseUri) {
        this.baseUri = telegramBaseUri;
    }

}
