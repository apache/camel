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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.ai.AuthoringTools;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolExecutionException;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.EDIT_TOOL;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.FILES_TOOL;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.SAVE_SUMMARY_TOOL;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.VALIDATE_TOOL;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiToolRegistry.WRITE_TOOL;

/**
 * The shared file tools in the TUI ({@code camel_get_files}, {@code camel_write_file}, {@code camel_edit_file},
 * {@code camel_validate_source}, {@code camel_save_project_summary}): they work on the source directory of the selected
 * integration, or of the one named. A file is written only there, after the user confirmed the change or watched it
 * replayed in the editor (the AI panel's /write mode); a directory given names the integration whose sources it holds.
 */
class TuiFileTools {

    static final Set<String> TOOLS = Set.of(FILES_TOOL, WRITE_TOOL, EDIT_TOOL, VALIDATE_TOOL, SAVE_SUMMARY_TOOL);
    private static final Set<String> WRITE_TOOLS = Set.of(WRITE_TOOL, EDIT_TOOL, SAVE_SUMMARY_TOOL);

    private final McpFacade facade;
    private final TuiToolRegistry registry;

    TuiFileTools(McpFacade facade, TuiToolRegistry registry) {
        this.facade = facade;
        this.registry = registry;
    }

    String execute(String name, Map<String, Object> args) {
        if (WRITE_TOOLS.contains(name) && args.get("directory") instanceof String d && !d.isBlank()) {
            String owner = facade.integrationWithSourceDirectory(d);
            if (owner == null || SAVE_SUMMARY_TOOL.equals(name) && !owner.equals(facade.getSelectedPid())) {
                JsonObject refused = new JsonObject();
                refused.put("status", "error");
                refused.put("error", d + " is not the source directory of " + (owner == null
                        ? "a running integration or an open project" : "the selected integration")
                                     + ": leave directory out to write to the selected integration's");
                return Jsoner.serialize(refused);
            }
            Map<String, Object> byOwner = new HashMap<>(args);
            byOwner.remove("directory");
            byOwner.put("name", owner);
            args = byOwner;
        }
        boolean hasDirectory = args.get("directory") instanceof String d && !d.isBlank();
        boolean named = args.get("name") instanceof String n && !n.isBlank();
        boolean facadeSelection = named || facade.getSelectedIntegrationName() != null;
        return switch (name) {
            case FILES_TOOL -> !hasDirectory && facadeSelection ? callGetFiles(args) : registry.executeShared(name, args);
            case WRITE_TOOL -> !hasDirectory && facadeSelection ? callWriteFile(args) : registry.executeShared(name, args);
            case EDIT_TOOL -> !hasDirectory && facadeSelection ? callEditFile(args) : registry.executeShared(name, args);
            case VALIDATE_TOOL -> !hasDirectory && facadeSelection && args.get("content") == null
                    ? callValidateSource(args) : registry.executeShared(name, args);
            case SAVE_SUMMARY_TOOL -> !hasDirectory && facadeSelection
                    ? callSaveSummary(args) : registry.executeShared(name, args);
            default -> registry.executeShared(name, args);
        };
    }

    /**
     * Saves the AI project summary of the selected integration through the TUI's write path, so the user sees and
     * confirms it like any file the model writes (CAMEL-25143).
     */
    private String callSaveSummary(Map<String, Object> args) {
        Path dir = facade.getSelectedSourceDirectory();
        if (dir == null) {
            return registry.executeShared(SAVE_SUMMARY_TOOL, args);
        }
        try {
            AuthoringTools.SummaryUpdate update
                    = AuthoringTools.summaryUpdate(dir, registry.sharedContext(), TuiToolRegistry.stringArgs(args));
            String name = args.get("name") instanceof String n ? n : null;
            JsonObject written = facade.writeFile(name, IntegrationSummary.FILE_NAME, update.content(), true);
            JsonObject result = update.result();
            result.put("status", written.get("status"));
            if (written.get("message") != null) {
                result.put("message", written.get("message"));
            }
            IntegrationSummaryHints.invalidate(dir);
            return result.toJson();
        } catch (ToolExecutionException e) {
            return "Error: " + e.getMessage();
        }
    }

    private String callGetFiles(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        JsonObject response = facade.getFiles(name, file);
        if (response == null) {
            return name != null
                    ? "No source files found for integration '" + name + "'"
                    : "No source files found for the selected integration";
        }
        return Jsoner.serialize(response);
    }

    private String callWriteFile(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        String content = args.get("content") instanceof String s ? s : null;
        boolean confirm = !Boolean.FALSE.equals(args.get("confirm"));
        // the write always validates: the tool has no switch, a model given one turns it off (CAMEL-24897)
        return Jsoner.serialize(facade.writeFile(name, file, content, confirm));
    }

    private String callEditFile(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        String find = args.get("find") instanceof String s ? s : null;
        String replace = args.get("replace") instanceof String s ? s : "";
        Object list = args.get("edits");
        String edits = list instanceof Map<?, ?> || list instanceof List<?> ? Jsoner.serialize(list)
                : list instanceof String text ? text : null;
        boolean confirm = !Boolean.FALSE.equals(args.get("confirm"));
        // an edit is confirmed and replayed in the editor like a write (CAMEL-24909), several edits as one (CAMEL-25501)
        return Jsoner.serialize(facade.editFile(name, file, find, replace, edits, confirm));
    }

    private String callValidateSource(Map<String, Object> args) {
        String name = args.get("name") instanceof String s ? s : null;
        String file = args.get("file") instanceof String s ? s : null;
        String content = args.get("content") instanceof String s ? s : null;
        return Jsoner.serialize(facade.validateSource(name, file, content));
    }
}
