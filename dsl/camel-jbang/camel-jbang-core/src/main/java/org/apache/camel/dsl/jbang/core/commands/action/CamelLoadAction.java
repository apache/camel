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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.File;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.ActionClient.ActionResult;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "load",
                     description = "Loads new source files into an existing Camel", sortOptions = false,
                     showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd load --source=myRoute.yaml" })
public class CamelLoadAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--source" },
                        description = "Source file(s) to load")
    List<String> source;

    @CommandLine.Option(names = { "--restart" },
                        description = "To force restart all routes after loading source files")
    boolean restart;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    public CamelLoadAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (source == null || source.isEmpty()) {
            throw CliException.usage("No source files provided. Specify files using --source option");
        }

        CamelProcess process = findOne(name);

        // turn into absolute path
        JsonArray arr = new JsonArray();
        for (String s : source) {
            File f = new File(s);
            if (f.exists() && f.isFile()) {
                // favour using absolute path to file as the load command can be called from another
                // folder than where camel is running
                s = f.getAbsolutePath();
            }
            arr.add(s);
        }

        ActionResult result = ActionClient.execute(process, "load", root -> {
            root.put("restart", restart);
            root.put("source", arr);
        }, timeout());
        if (!result.isOk()) {
            JsonObject cause = result.reply() != null ? result.reply().getMap("exception") : null;
            String st = cause != null ? cause.getString("stackTrace") : null;
            if (st != null) {
                printer().printDiagnostic(Jsoner.unescape(st));
            }
            throw new CliException(
                    result.exitCode(), "Error loading " + source.size() + " source files: " + result.error());
        }
        printer().println("Successfully loaded " + source.size() + " source files");
        return 0;
    }

}
