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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "expression",
                     description = "Evaluates Camel expression", sortOptions = false,
                     showDefaultValues = true)
public class EvalExpressionCommand extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--camel-version" },
                        description = "To run using a different Camel version than the default version.")
    String camelVersion;

    @CommandLine.Parameters(description = "Language to use", defaultValue = "simple")
    String language;

    @CommandLine.Parameters(description = "Whether to force evaluating as predicate", defaultValue = "false")
    boolean predicate;

    @CommandLine.Option(names = {
            "--template" },
                        description = "The template to use for evaluating (prefix with file: to refer to loading template from file)",
                        required = true)
    private String template;

    @CommandLine.Option(names = { "--body" },
                        description = "Message body (prefix with file: to refer to loading message body from file)")
    String body;

    @CommandLine.Option(names = { "--header" },
                        description = "Message header (key=value)")
    List<String> headers;

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "10000",
                        description = "Timeout in millis waiting for evaluation to be done")
    long timeout = 10000;

    @CommandLine.Option(names = { "--repo", "--repos" },
                        description = "Additional maven repositories (Use commas to separate multiple repositories)")
    String repositories;

    long pid;

    public EvalExpressionCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (template == null || template.isBlank()) {
            printer().printErr("Template is required");
            return -1;
        }
        if (template.startsWith("file:")) {
            Path f = Path.of(template.substring(5));
            if (!Files.exists(f)) {
                printer().printErr("Template file does not exist: " + f);
                return -1;
            }
        }

        Integer exit;
        List<Long> pids = findPids(name);
        if (pids.size() == 1) {
            this.pid = pids.get(0);
            printer().println("Using existing running Camel integration to evaluate (pid: " + this.pid + ")");
            exit = super.doCall();
        } else {
            try {
                // start a new empty camel in the background
                Run run = new Run(getMain());
                // requires camel 4.3 onwards
                if (camelVersion != null && VersionHelper.isLE(camelVersion, "4.2.0")) {
                    printer().printErr("This requires Camel version 4.3 or newer");
                    return -1;
                }
                exit = run.runTransformMessage(camelVersion, repositories);
                this.pid = run.spawnPid;
                if (exit == 0) {
                    exit = super.doCall();
                }
            } finally {
                if (pid > 0) {
                    // cleanup output file
                    Path outputFile = getOutputFile(Long.toString(pid));
                    PathUtils.deleteFile(outputFile);
                    // stop running camel as we are done
                    Path parent = CommandLineHelper.getCamelDir();
                    Path pidFile = parent.resolve(Long.toString(pid));
                    if (Files.exists(pidFile)) {
                        PathUtils.deleteFile(pidFile);
                    }
                }
            }
        }

        return exit;
    }

    @Override
    protected Integer doWatchCall() throws Exception {
        JsonObject root = new JsonObject();
        root.put("action", "eval");
        root.put("language", language);
        root.put("predicate", predicate);
        root.put("template", Jsoner.escape(template));
        if (body != null) {
            root.put("body", Jsoner.escape(body));
        }
        if (headers != null) {
            JsonArray arr = new JsonArray();
            for (String h : headers) {
                JsonObject jo = new JsonObject();
                if (!h.contains("=")) {
                    printer().println("Header must be in key=value format, was: " + h);
                    return 0;
                }
                jo.put("key", StringHelper.before(h, "="));
                jo.put("value", StringHelper.after(h, "="));
                arr.add(jo);
            }
            root.put("headers", arr);
        }

        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        Path f = getActionFile(Long.toString(pid));
        try {
            PathUtils.writeTextSafely(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            String status = jo.getString("status");
            if ("success".equals(status)) {
                String result = jo.getString("result");
                printer().println(result);
            } else {
                JsonObject cause = jo.getMap("exception");
                if (cause != null) {
                    String msg = cause.getString("message");
                    if (msg != null) {
                        msg = Jsoner.unescape(msg);
                    }
                    String st = cause.getString("stackTrace");
                    if (st != null) {
                        st = Jsoner.unescape(st);
                    }
                    if (msg != null) {
                        String text = Ansi.ansi().fgRed().a(msg).reset().toString();
                        printer().printErr(text);
                        printer().println();
                    }
                    if (st != null) {
                        String text = Ansi.ansi().fgRed().a(st).reset().toString();
                        printer().printErr(text);
                        printer().println();
                    }
                    return 1;
                }
            }
        }

        return 0;
    }

    protected JsonObject waitForOutputFile(Path outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < timeout) {
            try {
                // give time for response to be ready
                Thread.sleep(20);

                if (Files.exists(outputFile)) {
                    String text = Files.readString(outputFile);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

}
