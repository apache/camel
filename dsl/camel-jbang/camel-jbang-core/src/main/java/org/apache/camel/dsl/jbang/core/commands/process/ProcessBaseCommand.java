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
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

abstract class ProcessBaseCommand extends CamelCommand {

    private static final String[] DSL_EXT = new String[] { "groovy", "java", "js", "jsh", "kts", "xml", "yaml" };
    private static final Pattern PATTERN = Pattern.compile("([\\w|\\-.])+");

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

        final long cur = ProcessHandle.current().pid();
        final String pattern = name;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
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

        String name = extractCamelJBangName(cl);
        if (name == null) {
            name = extractCamelMavenName(cl);
        }
        if (name == null) {
            name = extractCamelMain(cl);
        }

        return name == null ? "" : name;
    }

    private static String extractCamelMavenName(String cl) {
        String name = StringHelper.before(cl, " camel:run");
        if (name != null) {
            if (name.contains("org.codehaus.plexus.classworlds.launcher.Launcher")) {
                return "mvn camel:run";
            }
        }

        return null;
    }

    private static String extractCamelMain(String cl) {
        if (cl != null) {
            if (cl.contains("camel-main")) {
                int pos = cl.lastIndexOf(" ");
                String after = cl.substring(pos);
                after = after.trim();
                if (after.matches("[\\w|.]+")) {
                    return "camel-main";
                }
            }
        }

        return null;
    }

    private static String extractCamelJBangName(String cl) {
        String name = StringHelper.after(cl, "main.CamelJBang run");
        if (name != null) {
            name = name.trim();
            StringJoiner js = new StringJoiner(" ");
            // focus only on the route files supported (to skip such as readme files)
            Matcher matcher = PATTERN.matcher(name);
            while (matcher.find()) {
                String part = matcher.group();
                String ext = FileUtil.onlyExt(part, true);
                if (ext != null && Arrays.asList(DSL_EXT).contains(ext)) {
                    js.add(part);
                }
            }
            return js.toString();
        }

        return null;
    }

    static long extractSince(ProcessHandle ph) {
        long since = 0;
        if (ph.info().startInstant().isPresent()) {
            since = ph.info().startInstant().get().toEpochMilli();
        }
        return since;
    }

}
