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
package org.apache.camel.dsl.jbang.core.common;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;

public final class ProcessHelper {

    private static final String[] DSL_EXT = new String[] { "groovy", "java", "js", "jsh", "kts", "xml", "yaml" };
    private static final Pattern PATTERN = Pattern.compile("([\\w|\\-.])+");

    private ProcessHelper() {
    }

    public static String extractName(JsonObject root, ProcessHandle ph) {
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
        String cl = ph != null ? ph.info().commandLine().orElse("") : "";

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to child)
        String mvn = extractMavenPluginName(cl);
        if (mvn != null && ph != null) {
            // is camel running in any of the children?
            boolean camel = ph.children().anyMatch(ch -> !extractName(root, ch).isEmpty());
            if (camel) {
                return ""; // skip parent as we want only the child process with camel
            }
        }

        // try first camel-jbang
        String name = extractCamelJBangName(cl);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // this may be a maven plugin run that spawns a child process where Camel actually runs (link to parent)
        mvn = extractMavenPluginName(cl);
        if (mvn == null && ph != null && ph.parent().isPresent()) {
            // try parent as it may spawn a sub process
            String clp = ph.parent().get().info().commandLine().orElse("");
            mvn = extractMavenPluginName(clp);
        }

        name = extractCamelName(cl, mvn);
        if (name == null && root != null) {
            JsonObject jo = (JsonObject) root.get("context");
            if (jo != null) {
                name = jo.getString("name");
            }
        }

        return name == null ? "" : name;
    }

    private static String extractMavenPluginName(String cl) {
        String name = StringHelper.after(cl, "org.codehaus.plexus.classworlds.launcher.Launcher");
        if (name != null) {
            return name.trim();
        }
        return null;
    }

    static String extractMainClass(JsonObject root) {
        JsonObject runtime = (JsonObject) root.get("runtime");
        return runtime != null ? runtime.getString("mainClass") : null;
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

}
