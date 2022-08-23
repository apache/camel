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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

abstract class ProcessBaseCommand extends CamelCommand {

    public ProcessBaseCommand(CamelJBangMain main) {
        super(main);
    }

    static List<Long> findPids(String name) {
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

        final String pattern = name;
        ProcessHandle.allProcesses()
                .forEach(ph -> {
                    String pName = extractName(ph);
                    // ignore file extension, so it is easier to match by name
                    pName = FileUtil.onlyName(pName);
                    if (!pName.isEmpty() && PatternHelper.matchPattern(pName, pattern)) {
                        pids.add(ph.pid());
                    }
                });

        return pids;
    }

    static String extractName(ProcessHandle ph) {
        String cl = ph.info().commandLine().orElse("");
        String name = StringHelper.after(cl, "main.CamelJBang run");
        if (name != null) {
            name = name.trim();
        } else {
            name = "";
        }
        return name;
    }

    static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

    static String maxWidth(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

}
