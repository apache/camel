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
package org.apache.camel.maven.packaging;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class MvelHelperTest {

    @Test
    public void shouldBeRobustAtEscaping() {
        assertThat(MvelHelper.escape(null)).isNull();
        assertThat(MvelHelper.escape("")).isEmpty();
        assertThat(MvelHelper.escape(" ")).isEqualTo(" ");
    }

    @ParameterizedTest
    @MethodSource("dollarEscapeCases")
    public void shouldEscapeDollarSigns(final String given, final String expected) {
        assertThat(MvelHelper.escape(given)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("curlyBracketEscapeCases")
    public void shouldcurlyBracket(final String given, final String expected) {
        assertThat(MvelHelper.escape(given)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("urlEscapeCases")
    public void shouldUrls(final String given, final String expected) {
        assertThat(MvelHelper.escape(given)).isEqualTo(expected);
    }

    static Stream<Arguments> dollarEscapeCases() {
        return Stream.of(arguments("$", "\\$"), arguments("some ${expression} here", "some \\$\\{expression\\} here"));
    }

    static Stream<Arguments> curlyBracketEscapeCases() {
        return Stream.of(arguments("some {expression} here", "some \\{expression\\} here"));
    }

    static Stream<Arguments> urlEscapeCases() {
        return Stream.of(arguments("http", "http"), arguments("some ${expression} here", "some \\$\\{expression\\} here"), arguments("http://example.com", "\\http://example.com"),
                         arguments("some http://example.com here", "some \\http://example.com here"), arguments("https://example.com", "\\https://example.com"),
                         arguments("ftp://example.com", "\\ftp://example.com"),
                         arguments("Sets the POST URL for zipkin's <a href=\"http://zipkin.io/zipkin-api/#/\">v2 api</a>, usually \"http://zipkinhost:9411/api/v2/spans\"",
                                   "Sets the POST URL for zipkin's <a href=\"http://zipkin.io/zipkin-api/#/\">v2 api</a>, usually \"\\http://zipkinhost:9411/api/v2/spans\""));
    }
}
