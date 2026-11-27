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
package org.apache.camel.component.geocoder;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Google client authenticates to the proxy only with Basic, so another proxyAuthMethod fails at startup. The proxy
 * is never contacted: the producer only builds its client when it starts.
 */
public class GeoCoderProxyAuthMethodTest {

    private static final String URI = "geocoder:address:Paris?apiKey=AIzaTest&proxyHost=127.0.0.1&proxyPort=0"
                                      + "&proxyAuthUsername=scott&proxyAuthPassword=tiger&proxyAuthMethod=";

    @ParameterizedTest
    @ValueSource(strings = { "Digest", "digest", "NTLM" })
    public void testUnsupportedProxyAuthMethodFailsAtStartup(String method) throws Exception {
        try (CamelContext context = createContext(method)) {
            assertThatThrownBy(context::start)
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported proxyAuthMethod " + method)
                    .hasMessageContaining("only Basic proxy authentication is supported");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "Basic", "basic" })
    public void testBasicProxyAuthMethodStarts(String method) throws Exception {
        try (CamelContext context = createContext(method)) {
            context.start();
            assertThat(context.isStarted()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "Basic", "basic", "BASIC" })
    public void testBasicProxyAuthMethodIsValidInTheCatalog(String method) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            RuntimeCamelCatalog catalog = context.getCamelContextExtension().getContextPlugin(RuntimeCamelCatalog.class);
            EndpointValidationResult result = catalog.validateProperties("geocoder", Map.of("proxyAuthMethod", method));
            assertThat(result.getInvalidEnum()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "Digest", "NTLM" })
    public void testUnsupportedProxyAuthMethodIsInvalidInTheCatalog(String method) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            RuntimeCamelCatalog catalog = context.getCamelContextExtension().getContextPlugin(RuntimeCamelCatalog.class);
            EndpointValidationResult result = catalog.validateProperties("geocoder", Map.of("proxyAuthMethod", method));
            assertThat(result.getInvalidEnum()).containsEntry("proxyAuthMethod", method);
        }
    }

    private static CamelContext createContext(String method) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(URI + method);
            }
        });
        return context;
    }
}
