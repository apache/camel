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

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "status", description = "List status of the running Camel applications")
public class CamelStatus extends ProcessBaseCommand {

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public CamelStatus(CamelJBangMain main) {
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
                        JsonObject status = loadStatus(ph.pid());
                        if (status != null) {
                            String state = status.getString("state").toLowerCase(Locale.ROOT);
                            Map<String, ?> stats = status.getMap("statistics");
                            if (stats != null) {
                                BigDecimal total = (BigDecimal) stats.get("exchangesTotal");
                                BigDecimal inflight = (BigDecimal) stats.get("exchangesInflight");
                                BigDecimal failed = (BigDecimal) stats.get("exchangesFailed");
                                System.out.printf("%s camel run %s %s (age: %s, total: %s, inflight: %s, failed: %s)%n",
                                        ph.pid(), name, state, ago, total, inflight, failed);
                            } else {
                                System.out.printf("%s camel run %s %s (age: %s)%n",
                                        ph.pid(), name, state, ago);
                            }
                        } else {
                            System.out.println(ph.pid() + " camel run " + name + " (age: " + ago + ")");
                        }
                    }
                });
        return 0;
    }

    private JsonObject loadStatus(long pid) {
        try {
            File f = getStatusFile("" + pid);
            if (f != null) {
                FileInputStream fis = new FileInputStream(f);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

}
