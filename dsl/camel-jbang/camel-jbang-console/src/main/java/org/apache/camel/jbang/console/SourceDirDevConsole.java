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
package org.apache.camel.jbang.console;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.console.ConsoleHelper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("source-dir")
public class SourceDirDevConsole extends AbstractDevConsole {

    /**
     * Whether to show the source in the output
     */
    public static final String SOURCE = "source";

    public SourceDirDevConsole() {
        super("camel", "source-dir", "Source Directory", "Information about Camel JBang source files");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String source = (String) options.get(SOURCE);

        final StringBuilder sb = new StringBuilder();

        RouteOnDemandReloadStrategy reload = getCamelContext().hasService(RouteOnDemandReloadStrategy.class);
        if (reload != null) {
            sb.append(String.format("Directory: %s%n", reload.getFolder()));
            // list files in this directory
            File dir = new File(reload.getFolder());
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    sb.append("Files:\n");
                    // sort files by name (ignore case)
                    Arrays.sort(files, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                    for (File f : files) {
                        boolean skip = f.getName().startsWith(".") || f.isHidden();
                        if (skip) {
                            continue;
                        }
                        boolean match = subPath == null || f.getName().startsWith(subPath) || f.getName().endsWith(subPath)
                                || PatternHelper.matchPattern(f.getName(), subPath);
                        if (match) {
                            long size = f.length();
                            long ts = f.lastModified();
                            String age = ts > 0 ? TimeUtils.printSince(ts) : "n/a";
                            sb.append(String.format("    %s (size: %d age: %s)%n", f.getName(), size, age));
                            if ("true".equals(source)) {
                                StringBuilder code = new StringBuilder();
                                try {
                                    LineNumberReader reader = new LineNumberReader(new FileReader(f));
                                    int i = 0;
                                    String t;
                                    do {
                                        t = reader.readLine();
                                        if (t != null) {
                                            i++;
                                            code.append(String.format("\n    #%s %s", i, t));
                                        }
                                    } while (t != null);
                                    IOHelper.close(reader);
                                } catch (Exception e) {
                                    // ignore
                                }
                                if (code.length() > 0) {
                                    sb.append("    ").append("-".repeat(40));
                                    sb.append(code);
                                    sb.append("\n\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String path = (String) options.get(Exchange.HTTP_PATH);
        String subPath = path != null ? StringHelper.after(path, "/") : null;
        String source = (String) options.get(SOURCE);

        JsonObject root = new JsonObject();

        RouteOnDemandReloadStrategy reload = getCamelContext().hasService(RouteOnDemandReloadStrategy.class);
        if (reload != null) {
            root.put("dir", reload.getFolder());
            // list files in this directory
            File dir = new File(reload.getFolder());
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    // sort files by name (ignore case)
                    Arrays.sort(files, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                    JsonArray arr = new JsonArray();
                    root.put("files", arr);
                    for (File f : files) {
                        boolean skip = f.getName().startsWith(".") || f.isHidden();
                        if (skip) {
                            continue;
                        }
                        boolean match = subPath == null || f.getName().startsWith(subPath) || f.getName().endsWith(subPath)
                                || PatternHelper.matchPattern(f.getName(), subPath);
                        if (match) {
                            JsonObject jo = new JsonObject();
                            jo.put("name", f.getName());
                            jo.put("size", f.length());
                            jo.put("lastModified", f.lastModified());
                            if ("true".equals(source)) {
                                try {
                                    List<JsonObject> code = ConsoleHelper.loadSourceAsJson(new FileReader(f), null);
                                    if (code != null) {
                                        jo.put("code", code);
                                    }
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                            arr.add(jo);
                        }
                    }
                }
            }
        }
        return root;
    }
}
