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
package org.apache.camel.openapi;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.spy;

public class RestOpenApiSupportTest {

    @Test
    public void shouldAdaptFromXForwardHeadersV3() {
        OpenAPI doc = new OpenAPI();
        doc.addServersItem(new Server().url("http://myhost/base"));
        final OpenAPI openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, "/prefix");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_HOST, "host");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, "http, HTTPS ");
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);

        assertEquals("http://host/prefix/base", openApi.getServers().get(0).getUrl());
        assertEquals("https://host/prefix/base", openApi.getServers().get(1).getUrl());

    }

    @ParameterizedTest
    @MethodSource("basePathAndPrefixVariations")
    public void shouldAdaptWithVaryingBasePathsAndPrefixesV3(
            final String prefix, final String basePath,
            final String expected) {
        OpenAPI doc = new OpenAPI();
        if (basePath != null) {
            doc.addServersItem(new Server().url("http://myhost/" + basePath));
        } else {
            doc.addServersItem(new Server().url("http://myhost/"));
        }
        final OpenAPI openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, prefix);
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);

        assertEquals(openApi.getServers().get(0).getUrl(), expected);
    }

    @ParameterizedTest
    @MethodSource("schemeVariations")
    public void shouldAdaptWithVaryingSchemesV3(final String xForwardedScheme, final String[] expected) {
        final OpenAPI openApi = spy(new OpenAPI());

        RestOpenApiSupport.setupXForwardedHeaders(openApi,
                Collections.singletonMap(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, xForwardedScheme));

        List<String> schemas = new ArrayList<String>();
        if (openApi.getServers() != null) {
            for (Server server : openApi.getServers()) {
                try {
                    URL url = new URL(server.getUrl());
                    schemas.add(url.getProtocol());
                } catch (MalformedURLException e) {

                }
            }
        }
        for (final String scheme : expected) {
            assertTrue(schemas.contains(scheme));
        }
    }

    static Stream<Arguments> basePathAndPrefixVariations() {
        return Stream.of(//
                arguments("/prefix", "/base", "/prefix/base"), //
                arguments("/prefix", "/base/", "/prefix/base/"), //
                arguments("/prefix", "base", "/prefix/base"), //
                arguments("/prefix", "base/", "/prefix/base/"), //
                arguments("/prefix", "", "/prefix"), //
                arguments("/prefix", null, "/prefix"), //
                arguments("/prefix/", "/base", "/prefix/base"), //
                arguments("/prefix/", "/base/", "/prefix/base/"), //
                arguments("/prefix/", "base", "/prefix/base"), //
                arguments("/prefix/", "base/", "/prefix/base/"), //
                arguments("/prefix/", "", "/prefix/"), //
                arguments("/prefix/", null, "/prefix/"), //
                arguments("prefix", "/base", "prefix/base"), //
                arguments("prefix", "/base/", "prefix/base/"), //
                arguments("prefix", "base", "prefix/base"), //
                arguments("prefix", "base/", "prefix/base/"), //
                arguments("prefix", "", "prefix"), //
                arguments("prefix", null, "prefix"), //
                arguments("prefix/", "/base", "prefix/base"), //
                arguments("prefix/", "/base/", "prefix/base/"), //
                arguments("prefix/", "base", "prefix/base"), //
                arguments("prefix/", "base/", "prefix/base/"), //
                arguments("prefix/", "", "prefix/"), //
                arguments("prefix/", null, "prefix/") //
        );
    }

    static Stream<Arguments> schemeVariations() {
        final String[] none = new String[0];

        return Stream.of(//
                arguments(null, none), //
                arguments("", none), //
                arguments(",", none), //
                arguments(" , ", none), //
                arguments("HTTPS,http", new String[] { "https", "http" }), //
                arguments(" HTTPS,  http ", new String[] { "https", "http" }), //
                arguments(",http,", new String[] { "http" }), //
                arguments("hTtpS", new String[] { "https" })//
        );
    }
}
