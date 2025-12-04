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

import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.impl.console.ConsoleHelper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(
        name = "source-dir",
        group = "camel-jbang",
        displayName = "Source Directory",
        description = "Information about Camel JBang source files")
public class SourceDirDevConsole extends AbstractDevConsole {

    /**
     * Whether to show the source in the output
     */
    public static final String SOURCE = "source";

    public SourceDirDevConsole() {
        super("camel-jbang", "source-dir", "Source Directory", "Information about Camel JBang source files");
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
            Path dir = Paths.get(reload.getFolder());
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> streams = Files.list(dir)) {
                    List<Path> files = streams.collect(Collectors.toList());
                    if (!files.isEmpty()) {
                        sb.append("Files:\n");
                        // sort files by name (ignore case)
                        files.sort((o1, o2) -> o1.getFileName()
                                .toString()
                                .compareToIgnoreCase(o2.getFileName().toString()));
                        for (Path f : files) {
                            String fileName = f.getFileName().toString();
                            boolean skip = fileName.startsWith(".") || Files.isHidden(f);
                            if (skip) {
                                continue;
                            }
                            boolean match = subPath == null
                                    || fileName.startsWith(subPath)
                                    || fileName.endsWith(subPath)
                                    || PatternHelper.matchPattern(fileName, subPath);
                            if (match) {
                                long size = Files.size(f);
                                long ts = Files.getLastModifiedTime(f).toMillis();
                                String age = ts > 0 ? TimeUtils.printSince(ts) : "n/a";
                                sb.append(String.format("    %s (size: %d age: %s)%n", fileName, size, age));
                                if ("true".equals(source)) {
                                    StringBuilder code = new StringBuilder();
                                    try (Reader fileReader = Files.newBufferedReader(f, StandardCharsets.UTF_8);
                                            LineNumberReader reader = new LineNumberReader(fileReader)) {
                                        int i = 0;
                                        String t;
                                        do {
                                            t = reader.readLine();
                                            if (t != null) {
                                                i++;
                                                code.append(String.format("\n    #%s %s", i, t));
                                            }
                                        } while (t != null);
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                    if (!code.isEmpty()) {
                                        sb.append("    ").append("-".repeat(40));
                                        sb.append(code);
                                        sb.append("\n\n");
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
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
            Path dir = Paths.get(reload.getFolder());
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> streams = Files.list(dir)) {
                    List<Path> files = streams.collect(Collectors.toList());
                    if (!files.isEmpty()) {
                        // sort files by name (ignore case)
                        files.sort((o1, o2) -> o1.getFileName()
                                .toString()
                                .compareToIgnoreCase(o2.getFileName().toString()));
                        JsonArray arr = new JsonArray();
                        root.put("files", arr);
                        for (Path f : files) {
                            String fileName = f.getFileName().toString();
                            boolean skip = fileName.startsWith(".") || Files.isHidden(f);
                            if (skip) {
                                continue;
                            }
                            boolean match = subPath == null
                                    || fileName.startsWith(subPath)
                                    || fileName.endsWith(subPath)
                                    || PatternHelper.matchPattern(fileName, subPath);
                            if (match) {
                                JsonObject jo = new JsonObject();
                                jo.put("name", fileName);
                                jo.put("size", Files.size(f));
                                jo.put(
                                        "lastModified",
                                        Files.getLastModifiedTime(f).toMillis());
                                if ("true".equals(source)) {
                                    try (Reader fileReader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                                        List<JsonObject> code = ConsoleHelper.loadSourceAsJson(fileReader, null);
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
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return root;
    }
}
