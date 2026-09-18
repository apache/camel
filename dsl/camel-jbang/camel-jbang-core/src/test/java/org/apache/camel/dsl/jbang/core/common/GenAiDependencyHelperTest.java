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
package org.apache.camel.dsl.jbang.core.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiDependencyHelperTest {

    private final CamelCatalog catalog = new DefaultCamelCatalog();

    @Test
    void addsAiObservabilityWhenGenAiComponentPresentAndObserveEnabled() {
        List<String> deps = new ArrayList<>(List.of("camel:langchain4j-chat"));

        GenAiDependencyHelper.addAiObservabilityIfNeeded(deps, new Properties(), true, catalog);

        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void addsAiObservabilityWhenGenAiPropertyEnabled() {
        List<String> deps = new ArrayList<>(List.of("mvn:org.apache.camel:camel-openai"));
        Properties properties = new Properties();
        properties.setProperty(GenAiDependencyHelper.AI_OBSERVABILITY_ENABLED, "true");

        GenAiDependencyHelper.addAiObservabilityIfNeeded(deps, properties, false, catalog);

        assertThat(deps).contains("camel:ai-observability");
    }

    @Test
    void skipsAiObservabilityWithoutGenAiArtifacts() {
        List<String> deps = new ArrayList<>(List.of("camel:timer"));

        GenAiDependencyHelper.addAiObservabilityIfNeeded(deps, new Properties(), true, catalog);

        assertThat(deps).doesNotContain("camel:ai-observability");
    }

    @Test
    void skipsAiObservabilityWhenExplicitlyDisabled() {
        List<String> deps = new ArrayList<>(List.of("camel:langchain4j-chat"));
        Properties properties = new Properties();
        properties.setProperty(GenAiDependencyHelper.AI_OBSERVABILITY_ENABLED, "false");

        GenAiDependencyHelper.addAiObservabilityIfNeeded(deps, properties, true, catalog);

        assertThat(deps).doesNotContain("camel:ai-observability");
    }

    @Test
    void detectsLangChain4jProviderJar() {
        Collection<String> deps = List.of("mvn:dev.langchain4j:langchain4j-ollama:1.0.0");

        assertThat(GenAiDependencyHelper.hasGenAiDependency(deps, catalog)).isTrue();
    }

    @Test
    void detectsGenAiComponentFromCatalogLabel() {
        assertThat(GenAiDependencyHelper.hasGenAiDependency(List.of("camel:openai"), catalog)).isTrue();
    }

    @Test
    void timerComponentIsNotGenAi() {
        assertThat(GenAiDependencyHelper.hasGenAiDependency(List.of("camel:timer"), catalog)).isFalse();
    }

    @Test
    void explicitAiObservabilityMavenDepDoesNotCountAsGenAiRouteDependency() {
        assertThat(GenAiDependencyHelper.hasGenAiDependency(
                List.of("mvn:org.apache.camel:camel-ai-observability"), catalog)).isFalse();
    }

    @Test
    void doesNotDuplicateAiObservabilityWhenExplicitlyProvided() {
        List<String> deps = new ArrayList<>(
                List.of(
                        "camel:openai",
                        "mvn:org.apache.camel:camel-ai-observability"));

        GenAiDependencyHelper.addAiObservabilityIfNeeded(deps, new Properties(), true, catalog);

        assertThat(deps).contains("mvn:org.apache.camel:camel-ai-observability");
        assertThat(deps.stream().filter("camel:ai-observability"::equals)).hasSize(0);
    }
}
