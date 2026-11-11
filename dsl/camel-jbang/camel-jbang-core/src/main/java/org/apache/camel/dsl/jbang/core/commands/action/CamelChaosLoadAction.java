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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "chaos-load",
                   description = "Load a chaos scenario into a running integration (camel-chaos, for development only)",
                   generateHelp = true,
                   footer = {
                           "A scenario is a file of the project, such as test/chaos/outage.chaos, with one",
                           "rule per line, # comments and an optional seed= line, which makes it fail the",
                           "same messages each time. The phases of an experiment come from from= and until=",
                           "of its rules. Loading it removes the rules there are and starts a new report,",
                           "then adds the rules of the scenario, all or none: a line that is not valid is",
                           "refused with its line number, and the rules there are stay.",
                           "",
                           "To start with a scenario: camel run --chaos=test/chaos/outage.chaos",
                           "",
                           "Without a name or pid, the scenario goes to the only integration that runs, or",
                           "the only one with chaos switched on; when that is not clear, the command fails",
                           "with the list of the running integrations (exit code 2).",
                           "",
                           "Examples:",
                           "  camel cmd chaos-load test/chaos/outage.chaos",
                           "  camel cmd chaos-load test/chaos/outage.chaos myApp" })
public class CamelChaosLoadAction extends CamelChaosAction {

    @Argument(index = "0", required = true, description = "The scenario file, such as test/chaos/outage.chaos")
    String file;

    @Argument(index = "1", description = "Name or pid of the running Camel integration (must match one); without it,"
                                         + " the only one running, or the only one with chaos switched on")
    String name;

    public CamelChaosLoadAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Path file = Path.of(this.file);
        if (!Files.isRegularFile(file)) {
            throw CliException.fileNotFound("The chaos scenario does not exist: " + this.file);
        }
        String text = Files.readString(file);
        CamelProcess process = findChaosTarget(name);
        String scenario = file.getFileName().toString();
        printer().println(describe(chaos(process, "load", root -> {
            root.put("name", scenario);
            root.put("text", text);
        }), scenario));
        return 0;
    }

    static String describe(JsonObject answer, String scenario) {
        List<Object> ids = answer.getCollection("ids");
        int count = ids != null ? ids.size() : 0;
        StringBuilder sb = new StringBuilder("Loaded chaos scenario ").append(scenario).append(": ").append(count)
                .append(count == 1 ? " rule" : " rules").append(" (the rules there were are removed, and the report"
                                                                + " starts again)");
        List<Object> words = answer.getCollection("descriptions");
        for (int i = 0; i < count; i++) {
            sb.append("\n  ").append(ids.get(i));
            if (words != null && words.size() == count && words.get(i) != null) {
                sb.append(": ").append(words.get(i));
            }
        }
        return sb + warnings(answer);
    }

    @Override
    protected String describe(JsonObject answer) {
        return describe(answer, "scenario");
    }
}
