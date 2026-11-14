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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.common.CommandTree;

/**
 * Groups the sub-commands of the camel CLI by functional category in its help, in place of a flat alphabetical list:
 * aesh lists the sub-commands by their help group, and the ones without a group last, under "Other".
 */
public final class GroupedCommandHelpRenderer {

    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Running", List.of("run", "dev", "stop", "restart", "ps", "log", "shell", "script"));
        GROUPS.put("Monitoring", List.of("status", "get", "top", "trace", "hawtio", "jolokia"));
        GROUPS.put("Actions", List.of("cmd", "bind"));
        GROUPS.put("Development",
                List.of("init", "export", "debug", "eval", "explain", "transform", "dirty", "doctor", "sbom", "nano"));
        GROUPS.put("Configuration", List.of("config", "dependency", "version", "update", "wrapper", "completion", "plugin"));
        GROUPS.put("Catalog", List.of("catalog", "doc", "infra"));
        GROUPS.put("AI", List.of("ask", "harden", "overview", "semantic"));
    }

    private GroupedCommandHelpRenderer() {
    }

    /**
     * Puts the sub-commands of the root command in their help groups, in the order of the groups.
     */
    public static void apply(CommandTree root) {
        int order = 0;
        for (Map.Entry<String, List<String>> entry : GROUPS.entrySet()) {
            for (String name : entry.getValue()) {
                CommandTree sub = root.getSubcommands().get(name);
                if (sub != null) {
                    sub.setHelpGroup(entry.getKey(), order);
                }
                order++;
            }
        }
    }
}
