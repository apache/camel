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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "heap-dump", description = "Write a heap dump (.hprof) file for deep memory analysis", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nThe .hprof file can be analyzed with tools like Eclipse MAT, VisualVM, or jhat.",
                 "%nExamples:",
                 "  camel cmd heap-dump",
                 "  camel cmd heap-dump --dump-name=mydump",
                 "  camel cmd heap-dump --dump-name=mydump --live=false" })
public class CamelHeapDump extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--dump-name" },
                        description = "File name for the heap dump (without .hprof extension)")
    String dumpName;

    @CommandLine.Option(names = { "--live" },
                        description = "Whether to dump only live objects", defaultValue = "true")
    boolean live = true;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "60000", converter = TimeoutConverter.class,
                        description = "How long to wait for the heap dump to be written, in millis or as a duration such as 2m")
    long timeout = 60000;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    public CamelHeapDump(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findOne(name);

        JsonObject jo = executeAction(process, "heap-dump", root -> {
            if (dumpName != null) {
                root.put("name", dumpName);
            }
            root.put("live", Boolean.toString(live));
        });
        String error = jo.getString("error");
        if (error != null) {
            throw CliException.failure("Heap dump failed: " + error);
        }
        String file = jo.getString("file");
        long size = jo.getLongOrDefault("size", 0);
        if (jsonOutput) {
            JsonObject answer = new JsonObject();
            answer.put("pid", process.pid());
            answer.put("file", file);
            answer.put("size", size);
            JsonOutput.print(printer(), answer);
        } else {
            printer().printf("Heap dump written to: %s (%s)%n", file, formatSize(size));
        }
        return 0;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }
}
