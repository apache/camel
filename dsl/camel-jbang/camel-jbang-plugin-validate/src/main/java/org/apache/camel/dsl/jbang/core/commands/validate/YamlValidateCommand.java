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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import com.networknt.schema.ValidationMessage;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "yaml", description = "Parse and validate YAML routes")
public class YamlValidateCommand extends CamelCommand {

    private static final String IGNORE_FILE = "application";

    @CommandLine.Parameters(description = { "The Camel YAML source files to parse." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    Path[] filePaths;
    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--json" },
                        description = "To dump validation report in JSon format")
    boolean json;

    public YamlValidateCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        YamlValidator validator = new YamlValidator();
        validator.init();

        Map<String, List<ValidationMessage>> reports = new LinkedHashMap<>();
        for (String n : files) {
            if (matchFile(n)) {
                var report = validator.validate(new File(n));
                reports.put(n, report);
            }
        }

        int count = errorCounts(reports);
        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation error detected (errors:").append(count).append(")\n\n");

            for (var e : reports.entrySet()) {
                String name = e.getKey();
                var report = e.getValue();
                if (!report.isEmpty()) {
                    sb.append("\tFile: ").append(name).append("\n");
                    for (var r : report) {
                        sb.append("\t\t").append(r.toString()).append("\n");
                    }
                    sb.append("\n");
                }
            }
            printer().println(sb.toString());

            return 1;
        } else {
            printer().println("Validation success (files:" + reports.size() + ")");
        }

        return 0;
    }

    private static boolean matchFile(String name) {
        String no = FileUtil.onlyName(name).toLowerCase(Locale.ROOT);
        if (IGNORE_FILE.equals(no)) {
            return false;
        }
        String ext = FileUtil.onlyExt(name);
        if (ext == null) {
            return false;
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return "yml".equals(ext) || "yaml".equals(ext);
    }

    private static int errorCounts(Map<String, List<ValidationMessage>> reports) {
        int count = 0;
        for (List<ValidationMessage> list : reports.values()) {
            if (!list.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    static class FilesConsumer extends CamelCommand.ParameterConsumer<YamlValidateCommand> {
        protected void doConsumeParameters(Stack<String> args, YamlValidateCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
