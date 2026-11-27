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

import java.net.http.HttpClient;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.URISupport;

@Component("telegram")
public class TelegramComponent extends HealthCheckComponent {
    public static final String BOT_API_DEFAULT_URL = "https://api.telegram.org";

    @Metadata(label = "security", security = "secret",
              description = "The token of the bot, used by every endpoint that does not set its own authorizationToken.")
    private String authorizationToken;

    @Metadata(label = "advanced",
              description = "To use a custom java.net.http.HttpClient for every endpoint that does not set its own client."
                            + " The endpoints then do not use the proxy options.")
    private HttpClient client;

    @Metadata(label = "advanced", defaultValue = BOT_API_DEFAULT_URL,
              description = "The base URI of the Telegram Bot API, for example of a local Bot API server or of a mock in"
                            + " tests. It is used by every endpoint that does not set its own baseUri.")
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
            throw new IllegalArgumentException(
                    "Unsupported endpoint type for uri " + URISupport.sanitizeUri(uri) + ", remaining " + remaining);
        }
        if (configuration.getBaseUri() == null) {
            configuration.setBaseUri(baseUri);
        }

        TelegramEndpoint endpoint = new TelegramEndpoint(uri, this, configuration, client);
        configuration.setAuthorizationToken(authorizationToken);
        setProperties(endpoint, parameters);

        if (endpoint.getConfiguration().getAuthorizationToken() == null) {
            throw new IllegalArgumentException(
                    "AuthorizationToken must be configured on either component or endpoint for telegram: "
                                               + URISupport.sanitizeUri(uri));
        }

        return endpoint;
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

    public void setBaseUri(String telegramBaseUri) {
        this.baseUri = telegramBaseUri;
    }

}
