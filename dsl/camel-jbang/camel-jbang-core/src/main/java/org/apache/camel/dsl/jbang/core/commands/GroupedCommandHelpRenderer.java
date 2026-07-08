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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine;

/**
 * Renders the camel CLI subcommand list grouped by functional category, replacing the default flat alphabetical list.
 */
public class GroupedCommandHelpRenderer implements CommandLine.IHelpSectionRenderer {

    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Running", List.of("run", "dev", "stop", "restart", "ps", "log", "shell", "script"));
        GROUPS.put("Monitoring", List.of("get", "top", "trace", "hawtio", "jolokia"));
        GROUPS.put("Actions", List.of("cmd", "bind"));
        GROUPS.put("Development",
                List.of("init", "export", "debug", "eval", "explain", "transform", "dirty", "doctor", "sbom", "nano"));
        GROUPS.put("Configuration", List.of("config", "dependency", "version", "update", "wrapper", "completion", "plugin"));
        GROUPS.put("Catalog", List.of("catalog", "doc", "infra"));
        GROUPS.put("AI", List.of("ask", "harden"));
    }

    @Override
    public String render(CommandLine.Help help) {
        Map<String, CommandLine> subcommands = help.commandSpec().subcommands();
        if (subcommands.isEmpty()) {
            return "";
        }

        int nameWidth = subcommands.keySet().stream().mapToInt(String::length).max().orElse(10);

        List<String> assigned = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : GROUPS.entrySet()) {
            List<CommandLine> present = new ArrayList<>();
            for (String name : entry.getValue()) {
                CommandLine sub = subcommands.get(name);
                if (sub != null) {
                    // don't print hidden commands (picocli hides them too), but still
                    // count them as assigned so they don't pop up under "Other"
                    if (!sub.getCommandSpec().usageMessage().hidden()) {
                        present.add(sub);
                    }
                    assigned.add(name);
                }
            }
            if (!present.isEmpty()) {
                sb.append(String.format("%n  %s:%n", entry.getKey()));
                for (CommandLine sub : present) {
                    appendCommand(sb, sub, nameWidth);
                }
            }
        }

        List<CommandLine> ungrouped = new ArrayList<>();
        for (Map.Entry<String, CommandLine> entry : subcommands.entrySet()) {
            if (!assigned.contains(entry.getKey())
                    && !entry.getValue().getCommandSpec().usageMessage().hidden()) {
                ungrouped.add(entry.getValue());
            }
        }
        if (!ungrouped.isEmpty()) {
            sb.append(String.format("%n  Other:%n"));
            for (CommandLine sub : ungrouped) {
                appendCommand(sb, sub, nameWidth);
            }
        }

        return sb.toString();
    }

    private static void appendCommand(StringBuilder sb, CommandLine sub, int nameWidth) {
        String name = sub.getCommandSpec().name();
        String[] desc = sub.getCommandSpec().usageMessage().description();
        String description = desc != null && desc.length > 0 ? desc[0] : "";
        sb.append(String.format("    %-" + nameWidth + "s   %s%n", name, description));
    }
}
