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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

abstract class ProcessBaseCommand extends CamelCommand {

    public ProcessBaseCommand(CamelJBangMain main) {
        super(main);
    }

    List<Long> findPids(String name) {
        List<Long> pids = new ArrayList<>();

        // we need to know the pids of the running camel integrations
        if (name.matches("\\d+")) {
            return List.of(Long.parseLong(name));
        } else {
            if (name.endsWith("!")) {
                // exclusive this name only
                name = name.substring(0, name.length() - 1);
            } else if (!name.endsWith("*")) {
                // lets be open and match all that starts with this pattern
                name = name + "*";
            }
        }

        final long cur = ProcessHandle.current().pid();
        final String pattern = name;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    // there must be a status file for the running Camel integration
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        // ignore file extension, so it is easier to match by name
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pattern)) {
                            pids.add(ph.pid());
                        } else {
                            // try camel context name
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pattern)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });

        return pids;
    }

    public static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

    JsonObject loadStatus(long pid) {
        try {
            Path f = getStatusFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    String sourceLocLine(String location) {
        while (StringHelper.countChar(location, ':') > 1) {
            location = location.substring(location.indexOf(':') + 1);
        }
        int pos = location.indexOf(':');
        // is the colon as scheme or line number
        String last = location.substring(pos + 1);
        boolean digits = last.matches("\\d+");
        if (!digits) {
            // it must be scheme so clip that
            location = location.substring(pos + 1);
        }
        location = FileUtil.stripPath(location);
        return location;
    }

}
