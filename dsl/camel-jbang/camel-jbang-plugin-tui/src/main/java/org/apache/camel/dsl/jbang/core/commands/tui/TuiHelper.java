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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Shared utility methods for TUI commands.
 */
final class TuiHelper {

    private TuiHelper() {
    }

    /**
     * Eagerly load classes used by the TUI input reader daemon thread and picocli post-processing. Without this, during
     * JVM shutdown the classloader may already be closing while the input reader thread is still trying to load these
     * classes lazily — causing ClassNotFoundException stack traces on exit.
     */
    static void preloadClasses(ClassLoader cl) {
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyModifiers", cl);
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyEvent", cl);
        ObjectHelper.loadClass("dev.tamboui.tui.event.KeyCode", cl);
        ObjectHelper.loadClass("picocli.CommandLine$IExitCodeGenerator", cl);
    }

    /**
     * Find PIDs of running Camel integrations matching the given name pattern.
     */
    static List<Long> findPids(String name, Function<String, Path> statusFileResolver) {
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid(), statusFileResolver);
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
        return pids;
    }

    /**
     * Load the status JSON for a given PID.
     */
    static JsonObject loadStatus(long pid, Function<String, Path> statusFileResolver) {
        try {
            Path f = statusFileResolver.apply(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Truncate a string to max length, appending an ellipsis if truncated.
     */
    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    /**
     * Convert an Object (typically from JSON) to a long value.
     */
    static long objToLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }
}
