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
package org.apache.camel.swagger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.swagger.models.Scheme;
import io.swagger.models.Swagger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class RestSwaggerSupportTest {

    @Test
    public void shouldAdaptFromXForwardHeaders() {
        final Swagger swagger = spy(new Swagger().basePath("/base"));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestSwaggerSupport.HEADER_X_FORWARDED_PREFIX, "/prefix");
        headers.put(RestSwaggerSupport.HEADER_X_FORWARDED_HOST, "host");
        headers.put(RestSwaggerSupport.HEADER_X_FORWARDED_PROTO, "http, HTTPS ");
        RestSwaggerSupport.setupXForwardedHeaders(swagger, headers);

        verify(swagger).getBasePath();
        verify(swagger).setBasePath("/prefix/base");
        verify(swagger).setHost("host");
        verify(swagger).addScheme(Scheme.HTTP);
        verify(swagger).addScheme(Scheme.HTTPS);
        verifyNoMoreInteractions(swagger);
    }

    @ParameterizedTest
    @MethodSource("basePathAndPrefixVariations")
    public void shouldAdaptWithVaryingBasePathsAndPrefixes(final String prefix, final String basePath,
        final String expected) {
        final Swagger swagger = spy(new Swagger().basePath(basePath));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(RestSwaggerSupport.HEADER_X_FORWARDED_PREFIX, prefix);
        RestSwaggerSupport.setupXForwardedHeaders(swagger, headers);

        verify(swagger).getBasePath();
        verify(swagger).setBasePath(expected);
        verifyNoMoreInteractions(swagger);
    }

    @ParameterizedTest
    @MethodSource("schemeVariations")
    public void shouldAdaptWithVaryingSchemes(final String xForwardedScheme, final Scheme[] expected) {
        final Swagger swagger = spy(new Swagger());

        RestSwaggerSupport.setupXForwardedHeaders(swagger,
            Collections.singletonMap(RestSwaggerSupport.HEADER_X_FORWARDED_PROTO, xForwardedScheme));

        for (final Scheme scheme : expected) {
            verify(swagger).addScheme(scheme);
        }

        verifyNoMoreInteractions(swagger);
    }

    @Test
    public void shouldNotAdaptFromXForwardHeadersWhenNoHeadersSpecified() {
        final Swagger swagger = spy(new Swagger());

        RestSwaggerSupport.setupXForwardedHeaders(swagger, Collections.emptyMap());

        verifyNoMoreInteractions(swagger);
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
        final Scheme[] none = new Scheme[0];

        return Stream.of(//
            arguments(null, none), //
            arguments("", none), //
            arguments(",", none), //
            arguments(" , ", none), //
            arguments("HTTPS,http", new Scheme[] {Scheme.HTTPS, Scheme.HTTP}), //
            arguments(" HTTPS,  http ", new Scheme[] {Scheme.HTTPS, Scheme.HTTP}), //
            arguments(",http,", new Scheme[] {Scheme.HTTP}), //
            arguments("hTtpS", new Scheme[] {Scheme.HTTPS})//
        );
    }
}
