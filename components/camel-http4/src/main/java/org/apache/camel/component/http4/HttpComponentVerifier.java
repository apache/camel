/**
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
package org.apache.camel.component.http4;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.impl.verifier.DefaultComponentVerifier;
import org.apache.camel.impl.verifier.ResultBuilder;
import org.apache.camel.impl.verifier.ResultErrorBuilder;
import org.apache.camel.impl.verifier.ResultErrorHelper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

final class HttpComponentVerifier extends DefaultComponentVerifier {
    private final HttpComponent component;

    HttpComponentVerifier(HttpComponent component) {
        super("http4", component.getCamelContext());
        
        this.component = component;
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // The default is success
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        Optional<String> uri = getOption(parameters, "httpUri", String.class);
        if (!uri.isPresent()) {
            // lack of httpUri is a blocking issue
            builder.error(ResultErrorHelper.requiresOption("httpUri", parameters));
        } else {
            builder.error(parameters, this::verifyHttpConnectivity);
        }

        return builder.build();
    }

    private void verifyHttpConnectivity(ResultBuilder builder, Map<String, Object> parameters) throws Exception {
        Optional<String> uri = getOption(parameters, "httpUri", String.class);

        CloseableHttpClient httpclient = createHttpClient(parameters);
        HttpUriRequest request = new HttpGet(uri.get());

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            int code = response.getStatusLine().getStatusCode();
            String okCodes = getOption(parameters, "okStatusCodeRange", String.class).orElse("200-299");

            if (!HttpHelper.isStatusCodeOk(code, okCodes)) {
                if (code == 401) {
                    // Unauthorized, add authUsername and authPassword to the list
                    // of parameters in error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatusLine().getReasonPhrase())
                            .parameter("authUsername")
                            .parameter("authPassword")
                            .build()
                    );
                } else if (code >= 300 && code < 400) {
                    // redirect
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatusLine().getReasonPhrase())
                            .parameter("httpUri")
                            .attribute(ComponentVerifier.HTTP_REDIRECT, true)
                            .attribute(ComponentVerifier.HTTP_REDIRECT_LOCATION, () -> HttpUtil.responseHeaderValue(response, "location"))
                            .build()
                    );
                } else if (code >= 400) {
                    // generic http error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(response.getStatusLine().getReasonPhrase())
                            .build()
                    );
                }
            }
        } catch (UnknownHostException e) {
            builder.error(
                ResultErrorBuilder.withException(e)
                    .parameter("httpUri")
                    .build()
            );
        }
    }

    // *********************************
    // Helpers
    // *********************************

    private Optional<HttpClientConfigurer> configureAuthentication(Map<String, Object> parameters) {
        Optional<String> authUsername = getOption(parameters, "authUsername", String.class);
        Optional<String> authPassword = getOption(parameters, "authPassword", String.class);

        if (authUsername.isPresent() && authPassword.isPresent()) {
            Optional<String> authDomain = getOption(parameters, "authDomain", String.class);
            Optional<String> authHost = getOption(parameters, "authHost", String.class);

            return Optional.of(
                new BasicAuthenticationHttpClientConfigurer(
                    authUsername.get(),
                    authPassword.get(),
                    authDomain.orElse(null),
                    authHost.orElse(null)
                )
            );
        }

        return Optional.empty();
    }

    private Optional<HttpClientConfigurer> configureProxy(Map<String, Object> parameters) {
        Optional<String> uri = getOption(parameters, "httpUri", String.class);
        Optional<String> proxyAuthHost = getOption(parameters, "proxyAuthHost", String.class);
        Optional<Integer> proxyAuthPort = getOption(parameters, "proxyAuthPort", Integer.class);

        if (proxyAuthHost.isPresent() && proxyAuthPort.isPresent()) {
            Optional<String> proxyAuthScheme = getOption(parameters, "proxyAuthScheme", String.class);
            Optional<String> proxyAuthUsername = getOption(parameters, "proxyAuthUsername", String.class);
            Optional<String> proxyAuthPassword = getOption(parameters, "proxyAuthPassword", String.class);
            Optional<String> proxyAuthDomain = getOption(parameters, "proxyAuthDomain", String.class);
            Optional<String> proxyAuthNtHost = getOption(parameters, "proxyAuthNtHost", String.class);

            if (!proxyAuthScheme.isPresent()) {
                proxyAuthScheme = Optional.of(HttpHelper.isSecureConnection(uri.get()) ? "https" : "http");
            }

            if (proxyAuthUsername != null && proxyAuthPassword != null) {
                return Optional.of(
                    new ProxyHttpClientConfigurer(
                        proxyAuthHost.get(),
                        proxyAuthPort.get(),
                        proxyAuthScheme.get(),
                        proxyAuthUsername.orElse(null),
                        proxyAuthPassword.orElse(null),
                        proxyAuthDomain.orElse(null),
                        proxyAuthNtHost.orElse(null))
                );
            } else {
                return Optional.of(
                    new ProxyHttpClientConfigurer(
                        proxyAuthHost.get(),
                        proxyAuthPort.get(),
                        proxyAuthScheme.get())
                );
            }
        }

        return Optional.empty();
    }

    private CloseableHttpClient createHttpClient(Map<String, Object> parameters) throws Exception {
        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();
        configureAuthentication(parameters).ifPresent(configurer::addConfigurer);
        configureProxy(parameters).ifPresent(configurer::addConfigurer);

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

        // Apply custom http client properties like httpClient.redirectsEnabled
        setProperties(builder, "httpClient.", parameters);
        setProperties(requestConfigBuilder, "httpClient.", parameters);

        return builder.setDefaultRequestConfig(requestConfigBuilder.build())
            .build();
    }
}
