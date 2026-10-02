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

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "route", description = "Transform Camel routes to XML, YAML or Java format", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel transform route hello.java --format=yaml",
                 "  camel transform route hello.xml --format=yaml",
                 "  camel transform route hello.camel.yaml --format=java" })
public class TransformRoute extends CamelCommand {

    public static class FormatCompletionCandidates implements Iterable<String> {

        public FormatCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("xml", "yaml", "java").iterator();
        }
    }

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then application.properties is used as source for which files to run.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used

    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = {
            "--output" },
                        description = "File or directory to store transformed files. If none provide then output is printed to console. Use clipboard as name to copy content into clipboard.")
    private String output;

    @CommandLine.Option(names = { "--format" },
                        completionCandidates = FormatCompletionCandidates.class,
                        description = "Output format (${COMPLETION-CANDIDATES}), if only yaml files are provided, the format defaults to xml and vice versa."
                                      + " Java is converted without running the routes, one route builder class per file")
    String format;

    @CommandLine.Option(names = { "--resolve-placeholders" }, defaultValue = "false",
                        description = "Whether to resolve property placeholders in the dumped output")
    boolean resolvePlaceholders;

    @CommandLine.Option(names = { "--uri-as-parameters" }, defaultValue = "true",
                        description = "Whether to expand URIs into separated key/value parameters (only in use for YAML format)")
    boolean uriAsParameters = true;

    @CommandLine.Option(names = { "--ignore-loading-error" },
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--compile" }, defaultValue = "false",
                        description = "Compile and run Java routes to transform them. By default Java routes are read "
                                      + "without compiling them, and compiled only when a route cannot be read that way")
    boolean compile;

    @CommandLine.Mixin
    MavenResolverMixin mavenResolver;

    public TransformRoute(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (format == null) {
            // Automatically transform to xml if all files are yaml
            if (files.stream().allMatch(file -> file.endsWith(".yaml"))) {
                format = "xml";
            } else {
                format = "yaml";
            }
        }

        if ("java".equals(format)) {
            return transformToJava();
        }

        String dump = output;
        // if no output then we want to print to console, so we need to write to a hidden file, and dump that file afterwards
        if (output == null || "clipboard".equals(output)) {
            dump = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/transform-output." + format;
        }
        Files.deleteIfExists(Path.of(dump));
        final String target = dump;

        if (!compile && !resolvePlaceholders && TransformJavaRoutes.applies(files)) {
            // Java routes read without compiling them: in milliseconds, and no code of the project runs
            TransformJavaRoutes.Result result = TransformJavaRoutes.transform(files, format, target, uriAsParameters);
            if (result.transformed()) {
                return printDump(target);
            }
            // a route the parser cannot read completely (a lambda, a value known only at runtime): compile them all
        }

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", format);
                main.addInitialProperty("camel.main.dumpRoutesInclude", "routes,rests,routeConfigurations,beans,dataFormats");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesResolvePlaceholders", Boolean.toString(resolvePlaceholders));
                main.addInitialProperty("camel.main.dumpRoutesUriAsParameters", Boolean.toString(uriAsParameters));
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                // turn debug off as this can otherwise include source location in dump
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                if (ignoreLoadingError) {
                    // turn off bean method validator if ignore loading error
                    main.addInitialProperty("camel.language.bean.validate", "false");
                }
            }
        };
        run.files = files;
        run.executionLimitOptions.maxSeconds = 1;
        run.mavenResolver = mavenResolver;
        Integer exit = run.runTransform(ignoreLoadingError);
        if (exit != null && exit != 0) {
            return exit;
        }

        return printDump(target);
    }

    /**
     * To Java, each file is converted without running it (CAMEL-25254): read into the model by the parser of its DSL
     * and written as a route builder class, printed, or written into the output directory (or file, for one file).
     */
    private Integer transformToJava() throws Exception {
        StringBuilder all = new StringBuilder();
        Path out = output != null && !"clipboard".equals(output) ? Path.of(output) : null;
        boolean toDirectory = out != null && (Files.isDirectory(out) || files.size() > 1);
        for (String f : files) {
            RouteDslConverter.Result r = RouteDslConverter.convert(Path.of(f), "java");
            if (!r.converted()) {
                printer().printErr(r.refused());
                return 1;
            }
            // what did not carry over goes at the top of the class, as the TUI writes it
            String java = RouteDslConverter.withNotes(r.content(), r.notes(), "java");
            if (out == null) {
                all.append(java).append('\n');
            } else if (toDirectory) {
                Files.createDirectories(out);
                Files.writeString(out.resolve(r.fileName()), java);
            } else {
                Files.writeString(out, java);
            }
        }
        if (out == null) {
            if ("clipboard".equals(output)) {
                StringSelection data = new StringSelection(all.toString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, data);
            }
            printer().println(all.toString().stripTrailing());
        }
        return 0;
    }

    private Integer printDump(String target) {
        if (output == null || "clipboard".equals(output)) {
            // load target file and print to console
            String dump = waitForDumpFile(Path.of(target));
            if (dump != null) {
                if ("clipboard".equals(output)) {
                    Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
                    StringSelection data = new StringSelection(dump);
                    c.setContents(data, data);
                }
                printer().println(dump);
            }
        }

        return 0;
    }

    protected String waitForDumpFile(Path dumpFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                if (Files.exists(dumpFile)) {
                    try (InputStream is = Files.newInputStream(dumpFile)) {
                        return IOHelper.loadText(is);
                    }
                }
                // give time for response to be ready
                Thread.sleep(100);
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
