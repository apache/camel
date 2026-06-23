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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;

/**
 * MCP Tool for translating Camel configuration properties between Camel runtimes (camel-main / jbang standalone,
 * camel-spring-boot, camel-quarkus).
 *
 * Most Camel properties are runtime-agnostic ({@code camel.main.*}, {@code camel.component.*}, etc.) and are returned
 * unchanged. The properties that actually differ between runtimes are the embedded HTTP server and management endpoints
 * ({@code camel.server.*} / {@code camel.management.*} are jbang-only) and the legacy {@code camel.springboot.*}
 * configuration that was harmonized to {@code camel.main.*} in Camel 4.5 and removed in 4.13.
 */
@ApplicationScoped
public class PropertiesTranslateTools {

    enum Runtime {
        MAIN("main"),
        SPRING_BOOT("spring-boot"),
        QUARKUS("quarkus");

        private final String label;

        Runtime(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Runtime fromLabel(String value) {
            if (value == null) {
                return null;
            }
            String v = value.trim().toLowerCase(Locale.ENGLISH);
            for (Runtime r : values()) {
                if (r.label.equals(v)) {
                    return r;
                }
            }
            // accept a few common aliases
            switch (v) {
                case "camel-main":
                case "jbang":
                case "standalone":
                    return MAIN;
                case "camel-spring-boot":
                case "springboot":
                case "spring":
                    return SPRING_BOOT;
                case "camel-quarkus":
                    return QUARKUS;
                default:
                    return null;
            }
        }
    }

    /**
     * Canonical translation table. Each entry maps a "logical" property to the per-runtime physical key. A {@code null}
     * value means the runtime has no direct equivalent (e.g. Spring Boot Actuator auto-enables management endpoints).
     */
    private static final Map<String, Map<Runtime, String>> CANONICAL = buildCanonical();

    private static Map<String, Map<Runtime, String>> buildCanonical() {
        Map<String, Map<Runtime, String>> m = new LinkedHashMap<>();
        m.put("http.port", entry(
                "camel.server.port",
                "server.port",
                "quarkus.http.port"));
        m.put("http.host", entry(
                "camel.server.host",
                "server.address",
                "quarkus.http.host"));
        m.put("http.path", entry(
                "camel.server.path",
                "server.servlet.context-path",
                "quarkus.http.root-path"));
        m.put("management.port", entry(
                "camel.management.port",
                "management.server.port",
                "quarkus.management.port"));
        m.put("management.path", entry(
                "camel.management.path",
                "management.endpoints.web.base-path",
                "quarkus.management.root-path"));
        m.put("management.enabled", entry(
                "camel.management.enabled",
                null, // Spring Boot Actuator auto-enables when on classpath
                "quarkus.management.enabled"));
        return m;
    }

    private static Map<Runtime, String> entry(String main, String springBoot, String quarkus) {
        // Use a HashMap (not Map.copyOf) because some runtimes legitimately have no equivalent
        // for a given canonical key (e.g. Spring Boot has no direct counterpart for
        // camel.management.enabled), and Map.copyOf rejects null values.
        Map<Runtime, String> e = new LinkedHashMap<>();
        e.put(Runtime.MAIN, main);
        e.put(Runtime.SPRING_BOOT, springBoot);
        e.put(Runtime.QUARKUS, quarkus);
        return e;
    }

    /**
     * Reverse index: per-runtime physical key -> canonical key. Built once at class load.
     */
    private static final Map<Runtime, Map<String, String>> KEY_TO_CANONICAL = buildKeyToCanonical();

    private static Map<Runtime, Map<String, String>> buildKeyToCanonical() {
        Map<Runtime, Map<String, String>> m = new LinkedHashMap<>();
        for (Runtime r : Runtime.values()) {
            Map<String, String> rev = new LinkedHashMap<>();
            for (Map.Entry<String, Map<Runtime, String>> e : CANONICAL.entrySet()) {
                String key = e.getValue().get(r);
                if (key != null) {
                    rev.put(key, e.getKey());
                }
            }
            m.put(r, rev);
        }
        return m;
    }

    /**
     * Camel property prefixes that are runtime-agnostic and must NOT be rewritten when changing runtime.
     */
    private static final Set<String> CAMEL_AGNOSTIC_PREFIXES = Set.of(
            "camel.main.",
            "camel.component.",
            "camel.dataformat.",
            "camel.language.",
            "camel.transformer.",
            "camel.beans.",
            "camel.routes.",
            "camel.routetemplate.",
            "camel.routeconfiguration.",
            "camel.routecontroller.",
            "camel.startupcondition.",
            "camel.health.",
            "camel.cluster.",
            "camel.lra.",
            "camel.metrics.",
            "camel.opentelemetry.",
            "camel.opentelemetry2.",
            "camel.tracing.",
            "camel.threadpool.",
            "camel.resilience4j.",
            "camel.faulttolerance.",
            "camel.kamelet.",
            "camel.rest.",
            "camel.ssl.",
            "camel.vault.",
            "camel.devconsole.",
            "camel.debug.");

    /**
     * Tool to translate Camel configuration property lines between Camel runtimes.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Translate Camel configuration property lines (e.g. lines from application.properties) between "
                        +
                        "Camel runtimes: camel-main (jbang/standalone), camel-spring-boot, and camel-quarkus. "
                        +
                        "Most camel.* properties are runtime-agnostic and are returned unchanged. The keys that "
                        +
                        "actually differ are the embedded HTTP server and management endpoint configuration "
                        +
                        "(camel.server.* / camel.management.* are jbang-only; camel-spring-boot uses server.* / "
                        +
                        "management.* via Spring Boot; camel-quarkus uses quarkus.http.* / quarkus.management.*). "
                        +
                        "Legacy camel.springboot.* keys are rewritten to camel.main.* (deprecated in 4.5, removed in "
                        +
                        "4.13). Properties without a known translation are passed through with status=unknown so the "
                        +
                        "caller can review them — the tool never guesses.")
    public PropertiesTranslateResult camel_properties_translate(
            @ToolArg(description = "Configuration property lines to translate. Can be a single line or multiple lines "
                                   + "separated by newlines.") String properties,
            @ToolArg(description = "Source Camel runtime: main, spring-boot, or quarkus.") String fromRuntime,
            @ToolArg(description = "Target Camel runtime: main, spring-boot, or quarkus.") String toRuntime) {

        if (properties == null || properties.isBlank()) {
            throw new ToolCallException("properties argument is required", null);
        }

        Runtime from = Runtime.fromLabel(fromRuntime);
        if (from == null) {
            throw new ToolCallException(
                    "fromRuntime argument is required and must be one of: main, spring-boot, quarkus", null);
        }
        Runtime to = Runtime.fromLabel(toRuntime);
        if (to == null) {
            throw new ToolCallException(
                    "toRuntime argument is required and must be one of: main, spring-boot, quarkus", null);
        }

        List<TranslatedLine> results = new ArrayList<>();
        int translated = 0;
        int unchanged = 0;
        int unknown = 0;
        int comments = 0;

        String[] lines = properties.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                results.add(new TranslatedLine(lineNumber, line, line, "comment", null));
                comments++;
                continue;
            }

            TranslatedLine result = translateLine(lineNumber, line, from, to);
            results.add(result);
            switch (result.status()) {
                case "translated" -> translated++;
                case "unchanged" -> unchanged++;
                case "unknown" -> unknown++;
                default -> {
                    // unreachable for non-comment lines
                }
            }
        }

        TranslateSummary summary = new TranslateSummary(results.size(), translated, unchanged, unknown, comments);
        return new PropertiesTranslateResult(from.label(), to.label(), summary, results);
    }

    private TranslatedLine translateLine(int lineNumber, String line, Runtime from, Runtime to) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return new TranslatedLine(
                    lineNumber, line, line, "unknown",
                    "Line is not in key=value form; passed through unchanged");
        }
        String rawKey = line.substring(0, eq);
        String value = line.substring(eq + 1);
        String key = rawKey.trim();

        // 1. Legacy cleanup: camel.springboot.* is deprecated and removed since 4.13.
        // Rewrite to camel.main.* regardless of source/target runtime.
        if (key.startsWith("camel.springboot.")) {
            String newKey = "camel.main." + key.substring("camel.springboot.".length());
            String translatedLine = newKey + "=" + value;
            return new TranslatedLine(
                    lineNumber, line, translatedLine, "translated",
                    "Legacy camel.springboot.* prefix rewritten to camel.main.* (deprecated since Camel 4.5, removed in 4.13)");
        }

        // 2. Known per-runtime physical keys -> look up the canonical, then re-emit for the target.
        String canonical = KEY_TO_CANONICAL.get(from).get(key);
        if (canonical != null) {
            if (from == to) {
                return new TranslatedLine(lineNumber, line, line, "unchanged", null);
            }
            String targetKey = CANONICAL.get(canonical).get(to);
            if (targetKey == null) {
                return new TranslatedLine(
                        lineNumber, line, line, "unknown",
                        "No equivalent in " + to.label() + " for canonical '" + canonical
                                                           + "' (e.g. Spring Boot Actuator auto-enables management endpoints when on the classpath)");
            }
            String translatedLine = targetKey + "=" + value;
            return new TranslatedLine(lineNumber, line, translatedLine, "translated", null);
        }

        // 3. Camel-agnostic prefixes are identical across runtimes.
        for (String prefix : CAMEL_AGNOSTIC_PREFIXES) {
            if (key.startsWith(prefix)) {
                return new TranslatedLine(lineNumber, line, line, "unchanged", null);
            }
        }

        // 4. Anything else (server.*, quarkus.datasource.*, spring.jpa.*, logging.*, etc.) is out of scope —
        // we never guess. Pass through unchanged with a note.
        return new TranslatedLine(
                lineNumber, line, line, "unknown",
                "No translation rule for this key; passed through unchanged");
    }

    // Result record classes for Jackson serialization

    public record PropertiesTranslateResult(String fromRuntime, String toRuntime, TranslateSummary summary,
            List<TranslatedLine> properties) {
    }

    public record TranslateSummary(int total, int translated, int unchanged, int unknown, int comments) {
    }

    public record TranslatedLine(int lineNumber, String original, String translated, String status, String note) {
    }
}
