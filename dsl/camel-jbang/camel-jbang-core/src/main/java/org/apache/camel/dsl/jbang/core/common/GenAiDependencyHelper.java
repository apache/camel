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

import java.util.Collection;
import java.util.Properties;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Adds optional GenAI observability dependencies using the same settings-driven approach as OpenTelemetry and LRA.
 * <p>
 * GenAI component and LangChain4j provider JARs are resolved by the existing silent-run download pipeline
 * ({@code DependencyDownloaderComponentResolver}, {@code KnownDependenciesResolver}) — not by scanning route source.
 * </p>
 */
public final class GenAiDependencyHelper {

    static final String AI_OBSERVABILITY_ENABLED = "camel.aiObservability.enabled";

    private static final String AI_OBSERVABILITY_ARTIFACT = "camel-ai-observability";
    private static final String AI_OBSERVABILITY_SCHEME = "ai-observability";

    private GenAiDependencyHelper() {
    }

    /**
     * Adds {@code camel:ai-observability} when GenAI artifacts are already in the dependency set and observability is
     * requested via {@code --observe} or {@code camel.aiObservability.enabled=true}.
     */
    public static void addAiObservabilityIfNeeded(Collection<String> deps, Properties properties, boolean observe) {
        addAiObservabilityIfNeeded(deps, properties, observe, new DefaultCamelCatalog());
    }

    static void addAiObservabilityIfNeeded(
            Collection<String> deps, Properties properties, boolean observe, CamelCatalog catalog) {
        if (!includeAiObservability(properties, observe)) {
            return;
        }
        if (!hasGenAiDependency(deps, catalog)) {
            return;
        }
        if (alreadyHasAiObservability(deps)) {
            return;
        }
        if (catalog.otherModel(AI_OBSERVABILITY_SCHEME) != null) {
            deps.add("camel:ai-observability");
        }
    }

    static boolean includeAiObservability(Properties properties, boolean observe) {
        String enabled = properties != null ? properties.getProperty(AI_OBSERVABILITY_ENABLED) : null;
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        return observe || "true".equalsIgnoreCase(enabled);
    }

    static boolean hasGenAiDependency(Collection<String> deps, CamelCatalog catalog) {
        for (String dep : deps) {
            if (dep == null || dep.isBlank()) {
                continue;
            }
            if (isGenAiCamelScheme(dep, catalog)) {
                return true;
            }
            if (isGenAiMavenArtifact(dep, catalog)) {
                return true;
            }
            if (isLangChain4jProviderJar(dep)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGenAiCamelScheme(String dep, CamelCatalog catalog) {
        if (!dep.startsWith("camel:")) {
            return false;
        }
        String scheme = dep.substring("camel:".length());
        int query = scheme.indexOf('?');
        if (query > 0) {
            scheme = scheme.substring(0, query);
        }
        if (AI_OBSERVABILITY_SCHEME.equals(scheme)) {
            return false;
        }
        ComponentModel model = catalog.componentModel(scheme);
        return model != null && isAiLabel(model.getLabel());
    }

    private static boolean isGenAiMavenArtifact(String dep, CamelCatalog catalog) {
        if (!dep.startsWith("mvn:")) {
            return false;
        }
        try {
            MavenGav gav = MavenGav.parseGav(dep.substring(4));
            String artifactId = gav.getArtifactId();
            if (artifactId == null || AI_OBSERVABILITY_ARTIFACT.equals(artifactId)) {
                return false;
            }
            ArtifactModel<?> model = catalog.modelFromMavenGAV(gav.getGroupId(), artifactId, gav.getVersion());
            return model != null && isAiLabel(model.getLabel());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean alreadyHasAiObservability(Collection<String> deps) {
        for (String dep : deps) {
            if (dep == null || dep.isBlank()) {
                continue;
            }
            if (dep.startsWith("camel:")) {
                String scheme = dep.substring("camel:".length());
                int query = scheme.indexOf('?');
                if (query > 0) {
                    scheme = scheme.substring(0, query);
                }
                if (AI_OBSERVABILITY_SCHEME.equals(scheme)) {
                    return true;
                }
            } else if (dep.startsWith("mvn:") && dep.contains(":" + AI_OBSERVABILITY_ARTIFACT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLangChain4jProviderJar(String dep) {
        return dep.contains("dev.langchain4j:langchain4j-");
    }

    private static boolean isAiLabel(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        for (String token : label.split(",")) {
            if ("ai".equals(token.trim())) {
                return true;
            }
        }
        return false;
    }
}
