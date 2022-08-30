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
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

abstract class ActionBaseCommand extends CamelCommand {

    private static final String[] DSL_EXT = new String[] { "groovy", "java", "js", "jsh", "kts", "xml", "yaml" };
    private static final Pattern PATTERN = Pattern.compile("([\\w|\\-.])+");

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
                        String pName = extractName(root, ph);
                        // ignore file extension, so it is easier to match by name
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pattern)) {
                            pids.add(ph.pid());
                        }
                    }
                });

        return pids;
    }

    static String extractMainClass(JsonObject root) {
        JsonObject runtime = (JsonObject) root.get("runtime");
        return runtime != null ? runtime.getString("mainClass") : null;
    }

    static String extractName(JsonObject root, ProcessHandle ph) {
        String name = doExtractName(root, ph);
        return FileUtil.stripPath(name);
    }

    static String doExtractName(JsonObject root, ProcessHandle ph) {
        // favour main class if known
        if (root != null) {
            String mc = extractMainClass(root);
            if (mc != null) {
                return mc;
            }
        }
        String cl = ph.info().commandLine().orElse("");

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to child)
        String mvn = extractMavenPluginName(cl);
        if (mvn != null) {
            // is camel running in any of the children?
            boolean camel = ph.children().anyMatch(ch -> !extractName(root, ch).isEmpty());
            if (camel) {
                return ""; // skip parent as we want only the child process with camel
            }
        }

        // try first camel-jbang
        String name = extractCamelJBangName(cl);
        if (name != null) {
            return name;
        }

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to parent)
        mvn = extractMavenPluginName(cl);
        if (mvn == null && ph.parent().isPresent()) {
            // try parent as it may spawn a sub process
            String clp = ph.parent().get().info().commandLine().orElse("");
            mvn = extractMavenPluginName(clp);
        }

        name = extractCamelName(cl, mvn);
        return name == null ? "" : name;
    }

    private static String extractMavenPluginName(String cl) {
        String name = StringHelper.after(cl, "org.codehaus.plexus.classworlds.launcher.Launcher");
        if (name != null) {
            return name.trim();
        }
        return null;
    }

    private static String extractCamelName(String cl, String mvn) {
        if (cl != null) {
            if (cl.contains("camel-spring-boot") && mvn != null) {
                int pos = cl.lastIndexOf(" ");
                if (pos != -1) {
                    String after = cl.substring(pos);
                    after = after.trim();
                    if (after.matches("[\\w|.]+")) {
                        return after;
                    }
                }
                return mvn;
            } else if (cl.contains("camel-quarkus") && mvn != null) {
                return mvn;
            } else {
                int pos = cl.lastIndexOf(" ");
                if (pos != -1) {
                    String after = cl.substring(pos);
                    after = after.trim();
                    if (after.matches("[\\w|.]+")) {
                        return after;
                    }
                }
                if (mvn != null) {
                    return mvn;
                }
                return cl.contains("camel-main") ? "camel-main" : "camel-core";
            }
        }

        return null;
    }

    static String extractCamelJBangName(String cl) {
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
