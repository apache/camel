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
package org.apache.camel.component.platform.http.vertx;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.util.ObjectHelper;

public final class VertxPlatformHttpServerSupport {
    private static final Pattern COMMA_SEPARATED_SPLIT_REGEX = Pattern.compile("\\s*,\\s*");

    private VertxPlatformHttpServerSupport() {
    }

    // *****************************
    //
    // Body Handler
    //
    // *****************************

    static Handler<RoutingContext> createBodyHandler(VertxPlatformHttpServerConfiguration configuration) {
        BodyHandler bodyHandler = BodyHandler.create();

        if (configuration.getMaxBodySize() != null) {
            bodyHandler.setBodyLimit(configuration.getMaxBodySize().longValueExact());
        }

        bodyHandler.setHandleFileUploads(configuration.getBodyHandler().isHandleFileUploads());
        bodyHandler.setUploadsDirectory(configuration.getBodyHandler().getUploadsDirectory());
        bodyHandler.setDeleteUploadedFilesOnEnd(configuration.getBodyHandler().isDeleteUploadedFilesOnEnd());
        bodyHandler.setMergeFormAttributes(configuration.getBodyHandler().isMergeFormAttributes());
        bodyHandler.setPreallocateBodyBuffer(configuration.getBodyHandler().isPreallocateBodyBuffer());

        return new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext event) {
                event.request().resume();
                bodyHandler.handle(event);
            }
        };
    }

    // *****************************
    //
    // CORS
    //
    // *****************************

    static Handler<RoutingContext> createCorsHandler(VertxPlatformHttpServerConfiguration configuration) {
        final VertxPlatformHttpServerConfiguration.Cors corsConfig = configuration.getCors();

        return new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext event) {
                final HttpServerRequest request = event.request();
                final HttpServerResponse response = event.response();
                final String origin = request.getHeader(HttpHeaders.ORIGIN);

                if (origin == null) {
                    event.next();
                } else {
                    final String requestedMethods = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
                    if (requestedMethods != null) {
                        processHeaders(response, HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, requestedMethods,
                                corsConfig.getMethods());
                    }

                    final String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
                    if (requestedHeaders != null) {
                        processHeaders(response, HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, requestedHeaders,
                                corsConfig.getHeaders());
                    }

                    final boolean allowsOrigin
                            = ObjectHelper.isEmpty(corsConfig.getOrigins()) || corsConfig.getOrigins().contains(origin);
                    if (allowsOrigin) {
                        response.headers().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                    }

                    response.headers().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

                    if (ObjectHelper.isNotEmpty(corsConfig.getExposedHeaders())) {
                        response.headers().set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                String.join(",", corsConfig.getExposedHeaders()));
                    }

                    if (request.method().equals(HttpMethod.OPTIONS)) {
                        if ((requestedHeaders != null || requestedMethods != null)
                                && corsConfig.getAccessControlMaxAge() != null) {
                            response.putHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE,
                                    String.valueOf(corsConfig.getAccessControlMaxAge().getSeconds()));
                        }
                        response.end();
                    } else {
                        event.next();
                    }
                }
            }
        };
    }

    private static void processHeaders(
            HttpServerResponse response, CharSequence header, String allowValues, Collection<String> values) {
        if (ObjectHelper.isEmpty(values)) {
            response.headers().set(header, allowValues);
        } else {
            Set<String> requestedValues = new HashSet<>();
            for (String requestedValue : COMMA_SEPARATED_SPLIT_REGEX.split(allowValues)) {
                requestedValues.add(requestedValue.toLowerCase());
            }

            String result = values.stream()
                    .filter(value -> requestedValues.contains(value.toLowerCase()))
                    .collect(Collectors.joining(","));

            if (ObjectHelper.isNotEmpty(result)) {
                response.headers().set(header, result);
            }
        }
    }

    // *****************************
    //
    // SSL
    //
    // *****************************

    static HttpServerOptions configureSSL(
            HttpServerOptions options, VertxPlatformHttpServerConfiguration configuration, CamelContext camelContext) {
        final SSLContextParameters sslParameters = configuration.getSslContextParameters() != null
                ? configuration.getSslContextParameters()
                : configuration.isUseGlobalSslContextParameters() ? camelContext.getSSLContextParameters() : null;

        if (sslParameters != null) {
            options.setSsl(true);
            options.setKeyCertOptions(new KeyCertOptions() {
                @Override
                public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
                    return createKeyManagerFactory(camelContext, sslParameters);
                }

                @Override
                public KeyCertOptions clone() {
                    return this;
                }
            });

            options.setTrustOptions(new TrustOptions() {
                @Override
                public TrustOptions clone() {
                    return this;
                }

                @Override
                public TrustManagerFactory getTrustManagerFactory(Vertx vertx) throws Exception {
                    return createTrustManagerFactory(camelContext, sslParameters);
                }
            });
        }

        return options;
    }

    private static KeyManagerFactory createKeyManagerFactory(
            CamelContext camelContext, SSLContextParameters sslContextParameters)
            throws GeneralSecurityException, IOException {
        final KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
        if (keyManagers == null) {
            return null;
        }

        String kmfAlgorithm = camelContext.resolvePropertyPlaceholders(keyManagers.getAlgorithm());
        if (kmfAlgorithm == null) {
            kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
        }

        KeyManagerFactory kmf;
        if (keyManagers.getProvider() == null) {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm);
        } else {
            kmf = KeyManagerFactory.getInstance(kmfAlgorithm,
                    camelContext.resolvePropertyPlaceholders(keyManagers.getProvider()));
        }

        char[] kmfPassword = null;
        if (keyManagers.getKeyPassword() != null) {
            kmfPassword = camelContext.resolvePropertyPlaceholders(keyManagers.getKeyPassword()).toCharArray();
        }

        KeyStore ks = keyManagers.getKeyStore() == null ? null : keyManagers.getKeyStore().createKeyStore();

        kmf.init(ks, kmfPassword);
        return kmf;
    }

    private static TrustManagerFactory createTrustManagerFactory(
            CamelContext camelContext, SSLContextParameters sslContextParameters)
            throws GeneralSecurityException, IOException {
        final TrustManagersParameters trustManagers = sslContextParameters.getTrustManagers();
        if (trustManagers == null) {
            return null;
        }

        TrustManagerFactory tmf = null;

        if (trustManagers.getKeyStore() != null) {
            String tmfAlgorithm = camelContext.resolvePropertyPlaceholders(trustManagers.getAlgorithm());
            if (tmfAlgorithm == null) {
                tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            }

            if (trustManagers.getProvider() == null) {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            } else {
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm,
                        camelContext.resolvePropertyPlaceholders(trustManagers.getProvider()));
            }

            KeyStore ks = trustManagers.getKeyStore() == null ? null : trustManagers.getKeyStore().createKeyStore();
            tmf.init(ks);
        }

        return tmf;
    }
}
