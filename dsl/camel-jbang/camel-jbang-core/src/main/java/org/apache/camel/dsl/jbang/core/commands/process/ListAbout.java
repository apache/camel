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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.dsl.jbang.core.commands.ai.AboutReport;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "about",
                   description = "Get what an integration does and what has happened: its purpose, what comes in, its routes, what goes out, and the last minutes",
                   generateHelp = true,
                   footer = {
                           "The purpose is the comment at the top of the main source file, else the",
                           "descriptions of the routes.",
                           "The last minutes come from the timeline the integration records (the timeline",
                           "dev console, on in profile dev).",
                           "",
                           "Examples:",
                           "  camel get about",
                           "  camel get about myapp --json" })
public class ListAbout extends ProcessWatchCommand {

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    private CamelCatalog catalog;

    public ListAbout(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        JsonArray all = new JsonArray();
        StringBuilder text = new StringBuilder();
        forEachIntegration(name, (ph, root) -> {
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            String integrationName = context.getString("name");
            if ("CamelJBang".equals(integrationName)) {
                integrationName = ProcessHelper.extractName(root, ph);
            }
            JsonObject runtime = (JsonObject) root.get("runtime");
            String directory = runtime != null ? runtime.getString("directory") : null;
            if (directory == null || !Files.isDirectory(Path.of(directory))) {
                printer().printWarn("Skipped " + integrationName + ": its source directory is not known");
                return;
            }
            Path dir = Path.of(directory);
            About.Model model = About.of(dir, ProjectOverview.analyze(dir, catalog()));
            JsonObject about = AboutReport.json(model, root, loadTimeline(ph.pid()));
            about.put("pid", Long.toString(ph.pid()));
            about.put("name", integrationName);
            about.put("camelVersion", context.getString("version"));
            about.put("directory", directory);
            all.add(about);
            if (!text.isEmpty()) {
                text.append('\n');
            }
            String title = integrationName + " (pid " + ph.pid() + ")" + " · Camel " + context.getString("version")
                           + " · up " + TimeUtils.printSince(extractSince(ph));
            text.append(AboutReport.text(title, about));
        });
        if (jsonOutput) {
            // a list, also for one integration, as the other listings (CAMEL-26929)
            printer().println(all.toJson());
        } else if (!text.isEmpty()) {
            printer().print(text.toString());
        }
        return 0;
    }

    private CamelCatalog catalog() {
        if (catalog == null) {
            catalog = new DefaultCamelCatalog();
        }
        return catalog;
    }

    /** Asks the integration for the timeline it records (with the exchanges, for what a recovery recovered from). */
    JsonObject loadTimeline(long pid) {
        JsonObject request = new JsonObject();
        request.put("action", "timeline");
        request.put("exchanges", "true");
        try {
            ActionClient.ActionResult result = ActionClient.execute(pid, request, ActionClient.DEFAULT_TIMEOUT);
            return result.isOk() && result.reply() != null && result.reply().containsKey("routes") ? result.reply() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
