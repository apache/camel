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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

/**
 * Validates application.properties files: every camel.* key against the Camel catalog, so a wrong key such as
 * camel.main.log-level or camel.main.streamCaching is reported before the application fails to start (CAMEL-24698).
 */
@CommandLine.Command(name = "properties", description = "Validate Camel configuration in properties files")
public class PropertiesValidateCommand extends CamelCommand {

    @CommandLine.Parameters(description = { "The properties files to validate." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    List<String> files = new ArrayList<>();

    public PropertiesValidateCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();

        Map<String, List<String>> reports = new LinkedHashMap<>();
        for (String n : files) {
            if (matchFile(n)) {
                String content = Files.readString(new File(n).toPath());
                reports.put(n, SourceValidator.validateProperties(content, catalog, null));
            } else {
                printer().println("WARN: Skipping non-properties file: " + n);
            }
        }

        int count = reports.values().stream().mapToInt(List::size).sum();
        if (count > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation error detected (errors:").append(count).append(")\n\n");
            for (var e : reports.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    sb.append("\tFile: ").append(e.getKey()).append("\n");
                    for (String msg : e.getValue()) {
                        sb.append("\t\t").append(msg).append("\n");
                    }
                    sb.append("\n");
                }
            }
            printer().println(sb.toString());
            return 1;
        }
        printer().println("Validation success (files:" + reports.size() + ")");
        return 0;
    }

    private static boolean matchFile(String name) {
        String ext = FileUtil.onlyExt(name, true);
        return ext != null && "properties".equals(ext.toLowerCase(Locale.ROOT));
    }

    static class FilesConsumer extends CamelCommand.ParameterConsumer<PropertiesValidateCommand> {
        protected void doConsumeParameters(Stack<String> args, PropertiesValidateCommand cmd) {
            cmd.files.add(args.pop());
        }
    }
}
