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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
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

    public CamelHeapDump(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 1;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        }

        long pid = pids.get(0);

        // ensure output file is deleted before executing action
        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "heap-dump");
        if (dumpName != null) {
            root.put("name", dumpName);
        }
        root.put("live", Boolean.toString(live));

        Path f = getActionFile(Long.toString(pid));
        try {
            Files.writeString(f, root.toJson());
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = getJsonObject(outputFile, 60000);
        if (jo != null) {
            String error = jo.getString("error");
            if (error != null) {
                printer().println("Heap dump failed: " + error);
                return 1;
            }
            String file = jo.getString("file");
            long size = jo.getLongOrDefault("size", 0);
            printer().printf("Heap dump written to: %s (%s)%n", file, formatSize(size));
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 60 seconds");
            return 1;
        }

        // delete output file after use
        PathUtils.deleteFile(outputFile);

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
