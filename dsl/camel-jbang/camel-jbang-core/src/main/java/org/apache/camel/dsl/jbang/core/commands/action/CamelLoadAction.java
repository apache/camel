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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "load",
                     description = "Loads new source files into an existing Camel", sortOptions = false,
                     showDefaultValues = true)
public class CamelLoadAction extends ActionBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--source" },
                        description = "Source file(s) to load")
    List<String> source;

    @CommandLine.Option(names = { "--restart" },
                        description = "To force restart all routes after loading source files")
    boolean restart;

    public CamelLoadAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (source == null || source.isEmpty()) {
            printer().printErr("No source files provided. Specify files using --source option");
            return 1;
        }

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }
        long pid = pids.get(0);

        // ensure output file is deleted before executing action
        Path outputFile = getOutputFile(Long.toString(pid));
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "load");
        root.put("restart", restart);
        // turn into absolute path
        JsonArray arr = new JsonArray();
        for (String s : source) {
            File f = new File(s);
            if (f.exists() && f.isFile()) {
                // favour using absolute path to file as the load command can be called from another
                // folder than where camel is running
                s = f.getAbsolutePath();
            }
            arr.add(s);
        }
        root.put("source", arr);
        Path f = getActionFile(Long.toString(pid));
        Files.writeString(f, root.toJson());

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            String status = jo.getString("status");
            if ("success".equals(status)) {
                printer().println("Successfully loaded " + source.size() + " source files");
            } else {
                printer().printErr("Error loading " + source.size() + " source files");
            }
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

        return 0;
    }

    protected JsonObject waitForOutputFile(Path outputFile) {
        StopWatch watch = new StopWatch();
        long wait = 10000;
        while (watch.taken() < wait) {
            File f = outputFile.toFile();
            try {
                // give time for response to be ready
                Thread.sleep(20);

                if (Files.exists(outputFile) && f.length() > 0) {
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
