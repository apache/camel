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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * What the readme tool of the AI panel ({@code tui_get_readme}) answers. The README of an integration; without one, the
 * facts derived from its route sources, so a question about what the integration does starts from them instead of
 * reading every file (CAMEL-25143). The project's integration summary, partly written by an AI, only when it is asked
 * for, and saying so: a model takes back what an AI wrote there as facts, mistakes included (CAMEL-26946).
 */
final class ReadmeTool {

    private ReadmeTool() {
    }

    static String answer(McpFacade facade, String name, boolean aiSummary) {
        JsonObject readme = facade.getReadme(name);
        Path dir = facade.getSourceDirectory(name);
        if (readme == null) {
            JsonObject summary = aiSummary ? aiSummary(dir) : null;
            if (summary != null) {
                return Jsoner.serialize(summary);
            }
            JsonObject facts = facts(dir);
            if (facts != null) {
                if (aiSummary) {
                    facts.put("note", "There is no " + IntegrationSummary.FILE_NAME
                                      + " yet: /overview in the AI panel writes one");
                }
                return Jsoner.serialize(facts);
            }
            return name != null
                    ? "No README found for integration '" + name + "'"
                    : "No README found for the selected integration";
        }
        JsonObject result = new JsonObject();
        String content = readme.getString("content");
        result.put("file", readme.getStringOrDefault("file", "README"));
        result.put("content", content != null ? content : "");
        if (dir != null && Files.isRegularFile(dir.resolve(IntegrationSummary.FILE_NAME))) {
            result.put("alsoSee", IntegrationSummary.FILE_NAME + ": an AI-assisted summary of the project (camel_get_files)");
        }
        return Jsoner.serialize(result);
    }

    /**
     * The project's integration summary: the view without the tool comments, whether an AI wrote parts of it and which
     * model, and whether those parts still match the routes. Null when the project has none.
     */
    static JsonObject aiSummary(Path dir) {
        IntegrationSummary.Summary summary = IntegrationSummary.read(dir);
        IntegrationSummaryDoc.Doc doc = summary != null ? IntegrationSummaryDoc.load(dir) : null;
        if (doc == null) {
            return null;
        }
        boolean aiWritten = !summary.ai().isEmpty();
        JsonObject result = new JsonObject();
        result.put("file", IntegrationSummary.FILE_NAME);
        result.put("aiWritten", aiWritten);
        if (aiWritten) {
            if (summary.model() != null) {
                result.put("model", summary.model());
            }
            if (summary.date() != null) {
                result.put("date", summary.date());
            }
            result.put("note", "The sections and route texts marked " + IntegrationSummary.AI_MARK
                               + " were written by an AI and may be wrong: check them against the route sources, and say"
                               + " so when you repeat them; the rest is derived from the sources");
        }
        result.put("content", doc.markdown());
        if (doc.title().contains("out of date")) {
            result.put("upToDate", false);
        }
        return result;
    }

    /**
     * The facts of the project derived from its route sources as they are now: entry points, routes, flows, error
     * handling and external systems, with nothing an AI wrote. Null when the project has no routes.
     */
    static JsonObject facts(Path dir) {
        String facts = IntegrationSummaryDoc.facts(dir);
        if (facts == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("source", "the route sources");
        result.put("aiWritten", false);
        result.put("content", facts);
        return result;
    }
}
