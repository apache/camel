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
package org.apache.camel.component.openfga;

import java.net.http.HttpClient;
import java.time.Duration;

import javax.net.ssl.SSLContext;

import dev.openfga.sdk.api.client.ApiClient;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.ClientCredentials;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.apache.camel.util.ObjectHelper;

/**
 * Builds the {@link OpenFgaClient} an endpoint or a security policy talks to OpenFGA through.
 */
public final class OpenFgaClientFactory {

    private OpenFgaClientFactory() {
    }

    /**
     * Creates a client for the given configuration.
     *
     * @param  configuration                where the server is, which store to ask, and how to authenticate
     * @param  sslContext                   the TLS configuration to use, or null for the JVM default
     * @throws FgaInvalidParameterException when the configuration is not usable, for example a missing store id
     */
    public static OpenFgaClient createClient(OpenFgaConfiguration configuration, SSLContext sslContext)
            throws FgaInvalidParameterException {
        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .apiUrl(configuration.getApiUrl())
                .storeId(configuration.getStoreId())
                .readTimeout(Duration.ofMillis(configuration.getReadTimeout()))
                .maxRetries(configuration.getMaxRetries());
        if (ObjectHelper.isNotEmpty(configuration.getAuthorizationModelId())) {
            clientConfiguration.authorizationModelId(configuration.getAuthorizationModelId());
        }
        Credentials credentials = createCredentials(configuration);
        if (credentials != null) {
            clientConfiguration.credentials(credentials);
        }
        return new OpenFgaClient(clientConfiguration, createApiClient(configuration, sslContext));
    }

    /**
     * Supplies the SDK's HTTP layer rather than letting it build its own, for two reasons.
     * <p/>
     * An {@link SSLContext} can only be set while an {@link HttpClient} is being built, so a client the SDK builds
     * itself can never present a client certificate or trust a private CA.
     * <p/>
     * And the connect timeout has nowhere else to go. {@code ClientConfiguration.connectTimeout} looks like the place
     * for it, but as of openfga-sdk 0.10.1 nothing in the SDK ever reads it back, and the builder it uses by default
     * sets no connect timeout either - so the connect phase would be bounded only by the operating system, which on
     * Linux is on the order of two minutes. The per-request timeout the SDK does apply eventually ends such a call, but
     * an option that is documented and silently ignored is worse than no option, and a policy decision point is the
     * wrong place to leave a thread parked.
     * <p/>
     * The HTTP version is pinned to 1.1 to match what the SDK's own builder does, so supplying a client does not
     * quietly change the protocol OpenFGA is spoken to over.
     */
    private static ApiClient createApiClient(OpenFgaConfiguration configuration, SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(configuration.getConnectTimeout()));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        // the SDK builds one HttpClient here and reuses it for every request, so there is nothing per-exchange to
        // close - which matters on the Java 17 baseline, where HttpClient is not AutoCloseable
        return new ApiClient(builder);
    }

    /**
     * Resolves how to authenticate to OpenFGA. A pre-shared token wins over the client-credentials flow when both are
     * configured, and a server with no authentication needs neither.
     */
    private static Credentials createCredentials(OpenFgaConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getApiToken())) {
            return new Credentials(new ApiToken(configuration.getApiToken()));
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientId())) {
            ClientCredentials clientCredentials = new ClientCredentials()
                    .clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .apiTokenIssuer(configuration.getApiTokenIssuer())
                    .apiAudience(configuration.getApiAudience());
            if (ObjectHelper.isNotEmpty(configuration.getScopes())) {
                clientCredentials.scopes(configuration.getScopes());
            }
            return new Credentials(clientCredentials);
        }
        return null;
    }
}
