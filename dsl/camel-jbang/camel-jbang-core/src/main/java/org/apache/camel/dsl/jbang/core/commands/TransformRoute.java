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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route", description = "Transform Camel routes to XML or YAML format", sortOptions = false)
public class TransformRoute extends CamelCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = {
            "--output" },
                        description = "File or directory to store transformed files. If none provide then output is printed to console.")
    private String output;

    @CommandLine.Option(names = { "--format" },
                        description = "Output format (xml or yaml)", defaultValue = "yaml")
    String format = "yaml";

    @CommandLine.Option(names = { "--resolve-placeholders" }, defaultValue = "false",
                        description = "Whether to resolve property placeholders in the dumped output")
    boolean resolvePlaceholders;

    @CommandLine.Option(names = { "--uri-as-parameters" },
                        description = "Whether to expand URIs into separated key/value parameters (only in use for YAML format "
                                      + "and recommended to enable when using Apache Camel Karavan)")
    boolean uriAsParameters;

    @CommandLine.Option(names = { "--ignore-loading-error" },
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    public TransformRoute(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {

        String dump = output;
        // if no output then we want to print to console, so we need to write to a hidden file, and dump that file afterwards
        if (output == null) {
            dump = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/transform-output." + format;
        }
        final String target = dump;

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", format);
                main.addInitialProperty("camel.main.dumpRoutesInclude", "routes,rests,routeConfigurations,beans");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesResolvePlaceholders", "" + resolvePlaceholders);
                main.addInitialProperty("camel.main.dumpRoutesUriAsParameters", "" + uriAsParameters);
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                main.addInitialProperty("camel.jbang.transform", "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                if (ignoreLoadingError) {
                    // turn off bean method validator if ignore loading error
                    main.addInitialProperty("camel.language.bean.validate", "false");
                }
            }
        };
        run.files = files;
        run.maxSeconds = 1;
        Integer exit = run.runTransform(ignoreLoadingError);
        if (exit != null && exit != 0) {
            return exit;
        }

        if (output == null) {
            // load target file and print to console
            dump = waitForDumpFile(new File(target));
            if (dump != null) {
                printer().println(dump);
            }
        }

        return 0;
    }

    protected String waitForDumpFile(File dumpFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (dumpFile.exists()) {
                    FileInputStream fis = new FileInputStream(dumpFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return text;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    static class FilesConsumer extends ParameterConsumer<TransformRoute> {
        @Override
        protected void doConsumeParameters(Stack<String> args, TransformRoute cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }

}
