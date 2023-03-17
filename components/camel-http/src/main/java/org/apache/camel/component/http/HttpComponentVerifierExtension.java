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
package org.apache.camel.component.http;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

final class HttpComponentVerifierExtension extends DefaultComponentVerifierExtension {

    HttpComponentVerifierExtension() {
        super("http");
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Default is success
        final ResultBuilder builder
                = ResultBuilder.withStatusAndScope(Result.Status.OK, ComponentVerifierExtension.Scope.PARAMETERS);
        // Make a copy to avoid clashing with parent validation
        final HashMap<String, Object> verifyParams = new HashMap<>(parameters);
        // Check if validation is rest-related
        final boolean isRest = verifyParams.entrySet().stream().anyMatch(e -> e.getKey().startsWith("rest."));

        if (isRest) {
            // Build the httpUri from rest configuration
            verifyParams.put("httpUri", buildHttpUriFromRestParameters(parameters));

            // Cleanup parameters map from rest related stuffs
            verifyParams.entrySet().removeIf(e -> e.getKey().startsWith("rest."));
        }

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, verifyParams);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        final ResultBuilder builder
                = ResultBuilder.withStatusAndScope(Result.Status.OK, ComponentVerifierExtension.Scope.CONNECTIVITY);
        // Make a copy to avoid clashing with parent validation
        final HashMap<String, Object> verifyParams = new HashMap<>(parameters);
        // Check if validation is rest-related
        final boolean isRest = verifyParams.entrySet().stream().anyMatch(e -> e.getKey().startsWith("rest."));

        if (isRest) {
            // Build the httpUri from rest configuration
            verifyParams.put("httpUri", buildHttpUriFromRestParameters(parameters));

            // Cleanup parameters from rest related stuffs
            verifyParams.entrySet().removeIf(e -> e.getKey().startsWith("rest."));
        }

        String httpUri = getOption(verifyParams, "httpUri", String.class).orElse(null);
        if (ObjectHelper.isEmpty(httpUri)) {
            builder.error(
                    ResultErrorBuilder.withMissingOption("httpUri")
                            .detail("rest", isRest)
                            .build());
        }

        try (CloseableHttpClient httpclient = createHttpClient(verifyParams)) {
            httpclient.execute(
                    new HttpGet(httpUri),
                    response -> {
                        int code = response.getCode();
                        String okCodes = getOption(verifyParams, "okStatusCodeRange", String.class).orElse("200-299");

                        if (!HttpHelper.isStatusCodeOk(code, okCodes)) {
                            if (code == 401) {
                                // Unauthorized, add authUsername and authPassword to the list
                                // of parameters in error
                                builder.error(
                                        ResultErrorBuilder.withHttpCode(code)
                                                .description(response.getReasonPhrase())
                                                .parameterKey("authUsername")
                                                .parameterKey("authPassword")
                                                .build());
                            } else if (code >= 300 && code < 400) {
                                // redirect
                                builder.error(
                                        ResultErrorBuilder.withHttpCode(code)
                                                .description(response.getReasonPhrase())
                                                .parameterKey("httpUri")
                                                .detail(VerificationError.HttpAttribute.HTTP_REDIRECT,
                                                        () -> HttpUtil.responseHeaderValue(response, "location"))
                                                .build());
                            } else if (code >= 400) {
                                // generic http error
                                builder.error(
                                        ResultErrorBuilder.withHttpCode(code)
                                                .description(response.getReasonPhrase())
                                                .build());
                            }
                        }
                        return null;
                    });

        } catch (UnknownHostException e) {
            builder.error(
                    ResultErrorBuilder.withException(e)
                            .parameterKey("httpUri")
                            .build());
        } catch (Exception e) {
            builder.error(ResultErrorBuilder.withException(e).build());
        }

        return builder.build();
    }

    // *********************************
    // Helpers
    // *********************************

    private String buildHttpUriFromRestParameters(Map<String, Object> parameters) {
        // We are doing rest endpoint validation but as today the endpoint
        // can't do any param substitution so the validation is performed
        // against the http uri
        String httpUri = getOption(parameters, "rest.host", String.class).orElse(null);
        String path = getOption(parameters, "rest.path", String.class).map(FileUtil::stripLeadingSeparator).orElse(null);

        if (ObjectHelper.isNotEmpty(httpUri) && ObjectHelper.isNotEmpty(path)) {
            httpUri = httpUri + "/" + path;
        }

        return httpUri;
    }

    private Optional<HttpClientConfigurer> configureAuthentication(
            Map<String, Object> parameters,
            HttpCredentialsHelper credentialsHelper) {
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
                            authHost.orElse(null),
                            credentialsHelper));
        }

        return Optional.empty();
    }

    private Optional<HttpClientConfigurer> configureProxy(
            Map<String, Object> parameters,
            HttpCredentialsHelper credentialsHelper) {
        Optional<String> uri = getOption(parameters, "httpUri", String.class);
        Optional<String> proxyAuthHost = getOption(parameters, "proxyAuthHost", String.class);
        Optional<Integer> proxyAuthPort = getOption(parameters, "proxyAuthPort", Integer.class);

        if (proxyAuthHost.isPresent() && proxyAuthPort.isPresent()) {
            Optional<String> proxyAuthScheme = getOption(parameters, "proxyAuthScheme", String.class);
            Optional<String> proxyAuthUsername = getOption(parameters, "proxyAuthUsername", String.class);
            Optional<String> proxyAuthPassword = getOption(parameters, "proxyAuthPassword", String.class);
            Optional<String> proxyAuthDomain = getOption(parameters, "proxyAuthDomain", String.class);
            Optional<String> proxyAuthNtHost = getOption(parameters, "proxyAuthNtHost", String.class);

            if (proxyAuthScheme.isEmpty()) {
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
                                proxyAuthNtHost.orElse(null),
                                credentialsHelper));
            } else {
                return Optional.of(
                        new ProxyHttpClientConfigurer(
                                proxyAuthHost.get(),
                                proxyAuthPort.get(),
                                proxyAuthScheme.get()));
            }
        }

        return Optional.empty();
    }

    private CloseableHttpClient createHttpClient(Map<String, Object> parameters) throws Exception {
        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();
        HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
        configureAuthentication(parameters, credentialsHelper).ifPresent(configurer::addConfigurer);
        configureProxy(parameters, credentialsHelper).ifPresent(configurer::addConfigurer);

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
