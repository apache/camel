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

import io.apicurio.datamodels.core.models.common.Server;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v3.models.Oas30Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

public class RestOpenApiSupportTest {

    @Test
    public void shouldAdaptFromXForwardHeaders() {
        Oas20Document doc = new Oas20Document();
        doc.basePath = "/base";
        final Oas20Document openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, "/prefix");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_HOST, "host");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, "http, HTTPS ");
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);


        assertEquals(openApi.basePath, "/prefix/base");
        assertEquals(openApi.host, "host");
        assertTrue(openApi.schemes.contains("http"));
        assertTrue(openApi.schemes.contains("https"));

    }

    @Test
    public void shouldAdaptFromXForwardHeadersV3() {
        Oas30Document doc = new Oas30Document();
        doc.addServer("http://myhost/base", null);
        final Oas30Document openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, "/prefix");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_HOST, "host");
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, "http, HTTPS ");
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);


        assertEquals(openApi.getServers().get(0).url, "http://host/prefix/base");
        assertEquals(openApi.getServers().get(1).url, "https://host/prefix/base");


    }

    @ParameterizedTest
    @MethodSource("basePathAndPrefixVariations")
    public void shouldAdaptWithVaryingBasePathsAndPrefixes(final String prefix, final String basePath,
        final String expected) {
        Oas20Document doc = new Oas20Document();
        doc.basePath = basePath;
        final Oas20Document openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, prefix);
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);

        assertEquals(openApi.basePath, expected);
    }

    @ParameterizedTest
    @MethodSource("basePathAndPrefixVariations")
    public void shouldAdaptWithVaryingBasePathsAndPrefixesV3(final String prefix, final String basePath,
        final String expected) {
        Oas30Document doc = new Oas30Document();
        if (basePath != null) {
            doc.addServer("http://myhost/" + basePath, null);
        } else {
            doc.addServer("http://myhost/", null);
        }
        final Oas30Document openApi = spy(doc);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestOpenApiSupport.HEADER_X_FORWARDED_PREFIX, prefix);
        RestOpenApiSupport.setupXForwardedHeaders(openApi, headers);

        assertEquals(openApi.getServers().get(0).url, expected);
    }

    @ParameterizedTest
    @MethodSource("schemeVariations")
    public void shouldAdaptWithVaryingSchemes(final String xForwardedScheme, final String[] expected) {
        final Oas20Document openApi = spy(new Oas20Document());

        RestOpenApiSupport.setupXForwardedHeaders(openApi,
            Collections.singletonMap(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, xForwardedScheme));

        for (final String scheme : expected) {
            assertTrue(openApi.schemes.contains(scheme));
        }

    }

    @ParameterizedTest
    @MethodSource("schemeVariations")
    public void shouldAdaptWithVaryingSchemesV3(final String xForwardedScheme, final String[] expected) {
        final Oas30Document openApi = spy(new Oas30Document());

        RestOpenApiSupport.setupXForwardedHeaders(openApi,
            Collections.singletonMap(RestOpenApiSupport.HEADER_X_FORWARDED_PROTO, xForwardedScheme));

        List<String> schemas = new ArrayList<String>();
        if (openApi.servers != null) {
            for (Server server : openApi.servers) {
                try {
                    URL url = new URL(server.url);
                    schemas.add(url.getProtocol());
                } catch (MalformedURLException e) {


                }
            }
        }
        for (final String scheme : expected) {
            assertTrue(schemas.contains(scheme));
        }

    }

    @Test
    public void shouldNotAdaptFromXForwardHeadersWhenNoHeadersSpecified() {
        final Oas20Document openApi = spy(new Oas20Document());

        RestOpenApiSupport.setupXForwardedHeaders(openApi, Collections.emptyMap());

        verifyNoInteractions(openApi);
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
            arguments("HTTPS,http", new String[] {"https", "http"}), //
            arguments(" HTTPS,  http ", new String[] {"https", "http"}), //
            arguments(",http,", new String[] {"http"}), //
            arguments("hTtpS", new String[] {"https"})//
        );
    }
}
