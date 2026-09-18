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
import java.util.Map;
import java.util.Stack;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import picocli.CommandLine;

/**
 * Validates any source file camel run would load: YAML routes (schema, endpoints, simple, bean references), properties
 * (camel.* keys), Java (compiles it), XSLT (compiles the stylesheet) and XML (well formed), with the same checks the
 * MCP tools and the TUI apply before writing a file (CAMEL-24698).
 */
@CommandLine.Command(name = "source", description = "Validate any Camel source file: YAML, properties, Java, XSLT, XML")
public class SourceValidateCommand extends CamelCommand {

    @CommandLine.Parameters(description = { "The source files to validate." },
                            arity = "1..99",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    List<String> files = new ArrayList<>();

    @CommandLine.Mixin
    CatalogVersionMixin catalogVersion;

    public SourceValidateCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // the catalog and schema of the Camel version and runtime asked for; the CLI's own without options
        CatalogVersionMixin.Loaded loaded = catalogVersion.load();
        CamelCatalog catalog = loaded.catalog();
        YamlValidator schemaValidator = loaded.camelVersion() != null ? catalogVersion.yamlValidator(loaded, false) : null;
        Map<String, List<String>> reports = new LinkedHashMap<>();
        for (String n : files) {
            File f = new File(n);
            if (!SourceValidator.isValidatableFile(n)) {
                printer().println("WARN: Skipping file: " + n);
                continue;
            }
            String content = Files.readString(f.toPath());
            File parent = f.getAbsoluteFile().getParentFile();
            reports.put(n, SourceValidator.validate(f.getName(), content, catalog, null,
                    parent != null ? parent.toPath() : null, schemaValidator));
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

    static class FilesConsumer extends CamelCommand.ParameterConsumer<SourceValidateCommand> {
        protected void doConsumeParameters(Stack<String> args, SourceValidateCommand cmd) {
            cmd.files.add(args.pop());
        }
    }
}
