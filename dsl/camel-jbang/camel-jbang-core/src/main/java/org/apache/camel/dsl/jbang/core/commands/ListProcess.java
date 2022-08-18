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

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "ps", description = "List running Camel applications")
class ListProcess extends CamelCommand {

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    private String sort;

    public ListProcess(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        ProcessHandle.allProcesses()
                .sorted((o1, o2) -> {
                    switch (sort) {
                        case "pid":
                            return Long.compare(o1.pid(), o2.pid());
                        case "name":
                            return extractName(o1).compareTo(extractName(o2));
                        case "age":
                            // we want newest in top
                            return Long.compare(extractSince(o1), extractSince(o2)) * -1;
                        default:
                            return 0;
                    }
                })
                .forEach(ph -> {
                    String name = extractName(ph);
                    if (ObjectHelper.isNotEmpty(name)) {
                        String ago = TimeUtils.printSince(extractSince(ph));
                        System.out.println(ph.pid() + " camel run " + name + " (age: " + ago + ")");
                    }
                });
        return 0;
    }

    private static String extractName(ProcessHandle ph) {
        String cl = ph.info().commandLine().orElse("");
        String name = StringHelper.after(cl, "main.CamelJBang run");
        if (name != null) {
            name = name.trim();
        } else {
            name = "";
        }
        return name;
    }

    private static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

}
