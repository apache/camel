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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import picocli.CommandLine;

@CommandLine.Command(name = "normalize",
                     description = "Normalize YAML routes to canonical (explicit) form")
public class YamlNormalizeCommand extends CamelCommand {

    private static final String IGNORE_FILE = "application";

    @CommandLine.Option(names = { "--output" },
                        description = "File or directory to write normalized output. If not specified, output is printed to console.")
    private String output;

    @CommandLine.Parameters(description = { "The Camel YAML source files to normalize." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    Path[] filePaths;
    List<String> files = new ArrayList<>();

    public YamlNormalizeCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<String> matched = new ArrayList<>();
        for (String n : files) {
            if (matchFile(n)) {
                matched.add(n);
            }
        }
        if (matched.isEmpty()) {
            return 0;
        }

        String dump = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/normalize-output.yaml";
        final String target = dump;

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", "yaml");
                main.addInitialProperty("camel.main.dumpRoutesInclude", "routes,rests,routeConfigurations,beans,dataFormats");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesResolvePlaceholders", "false");
                main.addInitialProperty("camel.main.dumpRoutesUriAsParameters", "true");
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                main.addInitialProperty("camel.language.bean.validate", "false");
            }
        };
        run.files = matched;
        run.executionLimitOptions.maxSeconds = 1;
        Integer exit = run.runTransform(true);
        if (exit != null && exit != 0) {
            return exit;
        }

        String normalized = waitForDumpFile(Path.of(target));
        if (normalized == null) {
            printer().printErr("Error normalizing files");
            return 1;
        }

        if (output != null) {
            Path outPath = Path.of(output);
            if (Files.isDirectory(outPath)) {
                outPath = outPath.resolve("normalized.yaml");
            }
            Files.writeString(outPath, normalized);
            printer().println("Normalized " + matched.size() + " file(s) to " + output);
        } else {
            printer().println(normalized);
        }

        return 0;
    }

    private String waitForDumpFile(Path dumpFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                Thread.sleep(100);

                if (Files.exists(dumpFile)) {
                    try (InputStream is = Files.newInputStream(dumpFile)) {
                        return IOHelper.loadText(is);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private static boolean matchFile(String name) {
        String no = FileUtil.onlyName(name, true).toLowerCase(Locale.ROOT);
        if (IGNORE_FILE.equals(no)) {
            return false;
        }
        String ext = FileUtil.onlyExt(name, true);
        if (ext == null) {
            return false;
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return "yml".equals(ext) || "yaml".equals(ext);
    }

    static class FilesConsumer extends CamelCommand.ParameterConsumer<YamlNormalizeCommand> {
        @Override
        protected void doConsumeParameters(Stack<String> args, YamlNormalizeCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
