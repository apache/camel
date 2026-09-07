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

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;

/**
 * Detects a local Ollama instance for {@code camel doctor} and the TUI doctor popup.
 */
public final class OllamaDoctorSupport {

    private static final String DEFAULT_OLLAMA_DISPLAY = "localhost:11434";

    private OllamaDoctorSupport() {
    }

    public record Status(boolean running, String baseUrl, List<String> models) {

        public static Status notRunning() {
            return new Status(false, null, List.of());
        }
    }

    /**
     * Probes {@code camel infra run ollama} PID files and the default {@code http://localhost:11434} endpoint.
     */
    public static Status detect() {
        return detect(LlmClient.create());
    }

    static Status detect(LlmClient client) {
        client.withApiType(LlmClient.ApiType.ollama);
        if (!client.detectEndpoint()) {
            return Status.notRunning();
        }
        return new Status(true, client.endpointUrl(), client.listModels());
    }

    public static String formatDisplayHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_OLLAMA_DISPLAY;
        }
        String host = baseUrl;
        if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        } else if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.isBlank() ? DEFAULT_OLLAMA_DISPLAY : host;
    }

    public static String formatModels(List<String> models) {
        if (models == null || models.isEmpty()) {
            return "no models pulled";
        }
        return models.stream().collect(Collectors.joining(", "));
    }

    public static String cliRunningLine(Status status) {
        return "Running at " + formatDisplayHost(status.baseUrl()) + " — models: " + formatModels(status.models());
    }

    public static String cliNotDetectedLine() {
        return "Not detected (optional — start for local AI with F8 in TUI)";
    }

    public static String tuiRunningSummary(Status status, int maxLength) {
        String summary = formatDisplayHost(status.baseUrl()) + " (" + modelCountLabel(status.models()) + ")";
        if (maxLength > 0 && summary.length() > maxLength) {
            return summary.substring(0, Math.max(0, maxLength - 3)) + "...";
        }
        return summary;
    }

    public static String modelCountLabel(List<String> models) {
        if (models == null || models.isEmpty()) {
            return "no models";
        }
        int count = models.size();
        return count + (count == 1 ? " model" : " models");
    }

    /**
     * Returns true when the model tag suggests fewer than 14B parameters.
     * Tool-calling in the TUI F8 panel requires at least 14B.
     */
    public static boolean isSmallModel(String name) {
        int colon = name.lastIndexOf(':');
        String tag = colon >= 0 ? name.substring(colon + 1).toLowerCase() : "";
        if (tag.matches("\\d+b.*")) {
            int b = tag.indexOf('b');
            try {
                int params = Integer.parseInt(tag.substring(0, b));
                return params < 14;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean allModelsSmall(List<String> models) {
        return models != null && !models.isEmpty() && models.stream().allMatch(OllamaDoctorSupport::isSmallModel);
    }
}
