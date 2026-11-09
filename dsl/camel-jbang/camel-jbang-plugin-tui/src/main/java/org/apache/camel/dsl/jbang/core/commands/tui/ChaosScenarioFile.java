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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The rules of the chaos screen saved as a scenario (CAMEL-26722): a file of the project, such as
 * {@code test/chaos/orders.chaos}, with one rule per line and the seed, which {@code camel cmd chaos-load} and
 * {@code camel run --chaos=<file>} read.
 */
final class ChaosScenarioFile {

    static final String EXTENSION = ".chaos";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ChaosScenarioFile() {
    }

    /** The file offered for an integration: {@code test/chaos/<name>.chaos}. */
    static String defaultPath(IntegrationInfo info) {
        String name = info.name != null ? info.name.replaceAll("[^A-Za-z0-9._-]", "-") : "";
        return "test/chaos/" + (name.isBlank() ? "scenario" : name) + EXTENSION;
    }

    /**
     * The file in the folder of the integration, with the extension added when it is left out.
     *
     * @return the file, or null when the integration has no folder or the path is empty
     */
    static Path resolve(IntegrationInfo info, String path) {
        if (info.directory == null || path == null || path.isBlank()) {
            return null;
        }
        String name = path.strip();
        if (!name.endsWith(EXTENSION)) {
            name = name + EXTENSION;
        }
        return Path.of(info.directory).resolve(name).normalize();
    }

    /** The scenario: a comment with where it came from, the seed, and the rules in the order of their ids. */
    static String text(IntegrationInfo info, ChaosInfo chaos, String path, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Chaos rules of ").append(info.name).append(", saved on ").append(WHEN.format(now)).append('\n');
        sb.append("# camel cmd chaos-load ").append(path).append(", or camel run --chaos=").append(path).append('\n');
        sb.append("# the times of from= and until= count from when the scenario is loaded\n");
        sb.append("seed=").append(chaos.seed).append('\n');
        for (ChaosInfo.Rule rule : chaos.rules) {
            if (rule.text != null && !rule.text.isBlank()) {
                sb.append(rule.text.strip()).append('\n');
            }
        }
        return sb.toString();
    }

    /** The number of rules the scenario of the chaos screen has. */
    static long count(ChaosInfo chaos) {
        return chaos.rules.stream().filter(r -> r.text != null && !r.text.isBlank()).count();
    }
}
