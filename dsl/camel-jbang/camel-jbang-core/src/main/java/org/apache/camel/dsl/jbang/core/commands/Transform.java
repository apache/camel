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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.main.KameletMain;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "transform", description = "Transform Camel route to XML or YAML format", sortOptions = false)
public class Transform extends CamelCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = {
            "--output" }, description = "File or directory to store transformed files", required = true)
    private String output;

    @CommandLine.Option(names = { "--format" },
                        description = "Output format (xml or yaml)", defaultValue = "yaml")
    String format = "yaml";

    @CommandLine.Option(names = { "--resolve-placeholders" }, defaultValue = "false",
                        description = "Whether to resolve property placeholders in the dumped output")
    boolean resolvePlaceholders;

    @CommandLine.Option(names = { "--uri-as-parameters" },
                        description = "Whether to expand URIs into separated key/value parameters (only in use for YAML format)")
    boolean uriAsParameters;

    public Transform(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", format);
                main.addInitialProperty("camel.main.dumpRoutesInclude", "routes,rests,routeConfigurations,beans");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesResolvePlaceholders", "" + resolvePlaceholders);
                main.addInitialProperty("camel.main.dumpRoutesUriAsParameters", "" + uriAsParameters);
                main.addInitialProperty("camel.main.dumpRoutesOutput", output);
            }
        };
        run.files = files;
        run.maxSeconds = 1;
        Integer exit = run.runSilent();
        if (exit != null && exit != 0) {
            return exit;
        }
        return 0;
    }

    static class FilesConsumer extends ParameterConsumer<Transform> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Transform cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
