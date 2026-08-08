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
package org.apache.camel.component.rest.postman.support;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostmanVariableResolverTest {

    private static PostmanVariableResolver resolver(Map<String, String> variables) {
        return new PostmanVariableResolver(variables, null, false);
    }

    @Test
    void shouldSubstituteKnownPlaceholders() {
        PostmanVariableResolver resolver = resolver(Map.of("baseUrl", "https://api.example.com"));

        assertThat(resolver.resolve("{{baseUrl}}/pet", "'x'")).isEqualTo("https://api.example.com/pet");
    }

    @Test
    void shouldSubstituteSeveralPlaceholdersInOneValue() {
        PostmanVariableResolver resolver = resolver(Map.of("a", "1", "b", "2"));

        assertThat(resolver.resolve("{{a}}-{{b}}", "'x'")).isEqualTo("1-2");
    }

    @Test
    void shouldTolerateWhitespaceInsideThePlaceholder() {
        PostmanVariableResolver resolver = resolver(Map.of("a", "1"));

        assertThat(resolver.resolve("{{ a }}", "'x'")).isEqualTo("1");
    }

    @Test
    void shouldResolveChainedPlaceholders() {
        PostmanVariableResolver resolver = resolver(Map.of("a", "{{b}}", "b", "final"));

        assertThat(resolver.resolve("{{a}}", "'x'")).isEqualTo("final");
    }

    @Test
    void shouldLeaveUnknownPlaceholdersVerbatim() {
        PostmanVariableResolver resolver = resolver(Map.of());

        assertThat(resolver.resolve("{{missing}}/pet", "'x'")).isEqualTo("{{missing}}/pet");
    }

    @Test
    void shouldTerminateOnCircularReferences() {
        PostmanVariableResolver resolver = resolver(Map.of("a", "{{b}}", "b", "{{a}}"));

        // the point is that this returns rather than looping forever; what it returns is unresolved either way
        assertThat(resolver.resolve("{{a}}", "'x'")).contains("{{");
    }

    @Test
    void shouldFailOnUnresolvedWhenConfiguredTo() {
        PostmanVariableResolver resolver = new PostmanVariableResolver(Map.of(), null, true);

        assertThatThrownBy(() -> resolver.resolve("{{missing}}", "request 'x'"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{missing}}")
                .hasMessageContaining("request 'x'")
                .hasMessageContaining("failOnUnresolvedVariable=false");
    }

    @Test
    void shouldPassThroughTextWithoutPlaceholders() {
        assertThat(resolver(Map.of("a", "1")).resolve("plain", "'x'")).isEqualTo("plain");
    }

    @Test
    void shouldPassThroughNull() {
        assertThat(resolver(Map.of()).resolve(null, "'x'")).isNull();
    }

    /**
     * A cloud-hosted collection is editable by anyone with access to the Postman workspace, so its content must not be
     * able to name a Camel property placeholder function and pull an environment variable into an outgoing request.
     */
    @Test
    void shouldNotResolvePropertyPlaceholderFunctionsFromCollectionContent() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PostmanVariableResolver resolver = new PostmanVariableResolver(Map.of(), context, false);

            assertThat(resolver.resolve("{{env:PATH}}", "'x'")).isEqualTo("{{env:PATH}}");
            assertThat(resolver.resolve("{{sys:user.home}}", "'x'")).isEqualTo("{{sys:user.home}}");
            assertThat(resolver.resolve("{{bean:foo}}", "'x'")).isEqualTo("{{bean:foo}}");
        }
    }

    @Test
    void shouldStillResolvePlainNamesFromCamelProperties() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getPropertiesComponent().setInitialProperties(propertiesOf("myBaseUrl", "https://from.properties"));
            context.start();
            PostmanVariableResolver resolver = new PostmanVariableResolver(Map.of(), context, false);

            assertThat(resolver.resolve("{{myBaseUrl}}", "'x'")).isEqualTo("https://from.properties");
        }
    }

    @Test
    void shouldPreferTheCollectionScopeOverCamelProperties() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getPropertiesComponent().setInitialProperties(propertiesOf("baseUrl", "https://from.properties"));
            context.start();
            PostmanVariableResolver resolver
                    = new PostmanVariableResolver(Map.of("baseUrl", "https://from.collection"), context, false);

            assertThat(resolver.resolve("{{baseUrl}}", "'x'")).isEqualTo("https://from.collection");
        }
    }

    private static Properties propertiesOf(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }

    @Test
    void shouldNotTreatReplacementValueAsARegexReplacement() {
        // a value containing $ or \ must be inserted literally, not interpreted as a group reference
        PostmanVariableResolver resolver = resolver(Map.of("token", "a$1b\\c"));

        assertThat(resolver.resolve("{{token}}", "'x'")).isEqualTo("a$1b\\c");
    }
}
