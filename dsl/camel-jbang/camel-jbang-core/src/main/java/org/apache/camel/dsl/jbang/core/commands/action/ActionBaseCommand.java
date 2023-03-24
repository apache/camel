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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

abstract class ActionBaseCommand extends CamelCommand {

    public ActionBaseCommand(CamelJBangMain main) {
        super(main);
    }

    List<Long> findPids(String name) {
        List<Long> pids = new ArrayList<>();

        // we need to know the pids of the running camel integrations
        if (name.matches("\\d+")) {
            return List.of(Long.parseLong(name));
        } else {
            // lets be open and match all that starts with this pattern
            if (!name.endsWith("*")) {
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
                        }
                    }
                });

        return pids;
    }

    static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

    static String extractState(int status) {
        if (status <= 4) {
            return "Starting";
        } else if (status == 5) {
            return "Running";
        } else if (status == 6) {
            return "Suspending";
        } else if (status == 7) {
            return "Suspended";
        } else if (status == 8) {
            return "Terminating";
        } else if (status == 9) {
            return "Terminated";
        } else {
            return "Terminated";
        }
    }

    JsonObject loadStatus(long pid) {
        try {
            File f = getStatusFile(Long.toString(pid));
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
