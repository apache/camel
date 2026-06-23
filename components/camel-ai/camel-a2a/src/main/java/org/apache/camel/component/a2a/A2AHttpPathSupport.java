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
package org.apache.camel.component.a2a;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;

final class A2AHttpPathSupport {

    private A2AHttpPathSupport() {
    }

    static String extractTaskId(String path, String basePath) {
        if (path == null) {
            return null;
        }
        String normalized = normalizeRequestPath(path, basePath);
        int tasksIdx = normalized.indexOf("/tasks/");
        if (tasksIdx >= 0) {
            String remaining = normalized.substring(tasksIdx + "/tasks/".length());
            int colonIdx = remaining.indexOf(':');
            if (colonIdx > 0) {
                return decodePathSegment(remaining.substring(0, colonIdx));
            }
            int slashIdx = remaining.indexOf('/');
            if (slashIdx > 0) {
                return decodePathSegment(remaining.substring(0, slashIdx));
            }
            return decodePathSegment(remaining);
        }
        return null;
    }

    static String[] extractPushConfigIds(String path, String basePath) {
        if (path == null || !path.contains("/pushNotificationConfigs/")) {
            return new String[] { null, null };
        }
        String normalized = normalizeRequestPath(path, basePath);
        int tasksIdx = normalized.indexOf("/tasks/");
        if (tasksIdx >= 0) {
            String afterTasks = normalized.substring(tasksIdx + "/tasks/".length());
            int pushIdx = afterTasks.indexOf("/pushNotificationConfigs/");
            if (pushIdx > 0) {
                String taskId = decodePathSegment(afterTasks.substring(0, pushIdx));
                String configId = decodePathSegment(afterTasks.substring(pushIdx + "/pushNotificationConfigs/".length()));
                return new String[] { taskId, configId };
            }
        }
        return new String[] { null, null };
    }

    static String parseQueryParameter(Exchange exchange, String param) {
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
            String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            if (name.equals(param)) {
                String rawValue = eq >= 0 ? pair.substring(eq + 1) : "";
                return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String normalizeRequestPath(String path, String basePath) {
        String normalized = path;
        int queryIdx = normalized.indexOf('?');
        if (queryIdx >= 0) {
            normalized = normalized.substring(0, queryIdx);
        }
        if (basePath != null && !basePath.isEmpty() && normalized.startsWith(basePath)) {
            normalized = normalized.substring(basePath.length());
        }
        return normalized;
    }

    private static String decodePathSegment(String value) {
        if (value == null) {
            return null;
        }
        return URI.create("http://localhost/" + value).getPath().substring(1);
    }
}
