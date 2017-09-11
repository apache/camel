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
package org.apache.camel.component.http;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.http.common.HttpHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;

final class HttpComponentVerifierExtension extends DefaultComponentVerifierExtension {

    HttpComponentVerifierExtension() {
        super("http");
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

        // Validate if the auth/proxy combination is properly set-up
        Optional<String> authMethod = getOption(parameters, "authMethod", String.class);
        if (authMethod.isPresent()) {
            // If auth method is set, username and password must be provided
            builder.error(ResultErrorHelper.requiresOption("authUsername", parameters));
            builder.error(ResultErrorHelper.requiresOption("authPassword", parameters));

            // Check if the AuthMethod is known
            AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, authMethod.get());
            if (auth != AuthMethod.Basic && auth != AuthMethod.Digest && auth != AuthMethod.NTLM) {
                builder.error(ResultErrorBuilder.withIllegalOption("authMethod", authMethod.get()).build());
            }

            // If auth method is NTLM, authDomain is mandatory
            if (auth == AuthMethod.NTLM) {
                builder.error(ResultErrorHelper.requiresOption("authDomain", parameters));
            }
        }

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

        HttpClient httpclient = createHttpClient(builder, parameters);
        HttpMethod method = new GetMethod(uri.get());

        try {
            int code = httpclient.executeMethod(method);
            String okCodes = getOption(parameters, "okStatusCodeRange", String.class).orElse("200-299");

            if (!HttpHelper.isStatusCodeOk(code, okCodes)) {
                if (code == 401) {
                    // Unauthorized, add authUsername and authPassword to the list
                    // of parameters in error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(method.getStatusText())
                            .parameterKey("authUsername")
                            .parameterKey("authPassword")
                            .build()
                    );
                } else if (code >= 300 && code < 400) {
                    // redirect
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(method.getStatusText())
                            .parameterKey("httpUri")
                            .detail(VerificationError.HttpAttribute.HTTP_REDIRECT, () -> HttpUtil.responseHeaderValue(method, "location"))
                            .build()
                    );
                } else if (code >= 400) {
                    // generic http error
                    builder.error(
                        ResultErrorBuilder.withHttpCode(code)
                            .description(method.getStatusText())
                            .build()
                    );
                }
            }
        } catch (UnknownHostException e) {
            builder.error(
                ResultErrorBuilder.withException(e)
                    .parameterKey("httpUri")
                    .build()
            );
        }
    }

    // *********************************
    // Helpers
    // *********************************

    private Optional<HttpClientConfigurer> configureAuthentication(ResultBuilder builder, Map<String, Object> parameters) {
        Optional<String> authMethod = getOption(parameters, "authMethod", String.class);

        if (authMethod.isPresent()) {
            Optional<String> authUsername = getOption(parameters, "authUsername", String.class);
            Optional<String> authPassword = getOption(parameters, "authPassword", String.class);

            if (authUsername.isPresent() && authUsername.isPresent()) {
                AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, authMethod.get());
                if (auth == AuthMethod.Basic || auth == AuthMethod.Digest) {
                    return Optional.of(
                        new BasicAuthenticationHttpClientConfigurer(false, authUsername.get(), authPassword.get())
                    );
                } else if (auth == AuthMethod.NTLM) {
                    Optional<String> authDomain = getOption(parameters, "authDomain", String.class);
                    Optional<String> authHost = getOption(parameters, "authHost", String.class);

                    if (!authDomain.isPresent()) {
                        builder.error(ResultErrorBuilder.withMissingOption("authDomain").build());
                    } else {
                        return Optional.of(
                            new NTLMAuthenticationHttpClientConfigurer(false, authUsername.get(), authPassword.get(), authDomain.get(), authHost.orElse(null))
                        );
                    }
                } else {
                    builder.error(ResultErrorBuilder.withIllegalOption("authMethod", authMethod.get()).build());
                }
            } else {
                builder.error(ResultErrorHelper.requiresOption("authUsername", parameters));
                builder.error(ResultErrorHelper.requiresOption("authPassword", parameters));
            }
        }
        return Optional.empty();
    }

    private Optional<HttpClientConfigurer> configureProxy(ResultBuilder builder, Map<String, Object> parameters) {
        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();

        // Add a Proxy
        Optional<String> proxyHost = getOption(parameters, "proxyAuthHost", String.class);
        if (!proxyHost.isPresent()) {
            proxyHost = getOption(parameters, "proxyHost", String.class);
        }

        Optional<Integer> proxyPort = getOption(parameters, "proxyAuthPort", Integer.class);
        if (!proxyPort.isPresent()) {
            proxyPort = getOption(parameters, "proxyPort", Integer.class);
        }

        if (proxyHost.isPresent() || proxyPort.isPresent()) {
            configurer.addConfigurer(new HttpProxyConfigurer(proxyHost, proxyPort));
        }


        // Configure proxy auth
        Optional<String> authMethod = getOption(parameters, "proxyAuthMethod", String.class);
        if (authMethod.isPresent()) {
            Optional<String> authUsername = getOption(parameters, "proxyAuthUsername", String.class);
            Optional<String> authPassword = getOption(parameters, "proxyAuthPassword", String.class);

            if (authUsername.isPresent() && authUsername.isPresent()) {
                AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, authMethod);
                if (auth == AuthMethod.Basic || auth == AuthMethod.Digest) {
                    configurer.addConfigurer(
                        new BasicAuthenticationHttpClientConfigurer(false, authUsername.get(), authPassword.get())
                    );
                } else if (auth == AuthMethod.NTLM) {
                    Optional<String> authDomain = getOption(parameters, "proxyAuthDomain", String.class);
                    Optional<String> authHost = getOption(parameters, "proxyAuthHost", String.class);

                    if (!authDomain.isPresent()) {
                        builder.error(ResultErrorBuilder.withMissingOption("authDomain").build());
                    } else {
                        return Optional.of(
                            new NTLMAuthenticationHttpClientConfigurer(false, authUsername.get(), authPassword.get(), authDomain.get(), authHost.orElse(null))
                        );
                    }
                } else {
                    builder.error(ResultErrorBuilder.withIllegalOption("authMethod", authMethod.get()).build());
                }
            } else {
                builder.error(ResultErrorHelper.requiresOption("authUsername", parameters));
                builder.error(ResultErrorHelper.requiresOption("authPassword", parameters));
            }
        }

        return Optional.of(configurer);
    }

    private HttpClient createHttpClient(ResultBuilder builder, Map<String, Object> parameters) throws Exception {
        HttpClientParams clientParams = setProperties(new HttpClientParams(), "httpClient.", parameters);
        HttpClient client = new HttpClient(clientParams);

        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();
        configureProxy(builder, parameters).ifPresent(configurer::addConfigurer);
        configureAuthentication(builder, parameters).ifPresent(configurer::addConfigurer);

        configurer.configureHttpClient(client);

        return client;
    }
}
