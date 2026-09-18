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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.ai.SourceValidator;
import org.apache.camel.dsl.yaml.validator.YamlValidator;
import org.apache.camel.util.FileUtil;
import picocli.CommandLine;

@CommandLine.Command(name = "yaml", description = "Parse and validate YAML routes")
public class YamlValidateCommand extends CamelCommand {

    private static final String IGNORE_FILE = "application";

    @CommandLine.Option(names = { "--canonical" }, defaultValue = "false",
                        description = "Validate against the canonical schema: reports the deprecated compact notation (string shorthands,"
                                      + " implicit expressions) with the canonical form to write")
    boolean canonical;

    @CommandLine.Option(names = { "--catalog" }, defaultValue = "true",
                        description = "Also check endpoint URIs and simple expressions against the Camel catalog"
                                      + " (use --catalog=false for the schema only)")
    boolean catalog = true;

    @CommandLine.Mixin
    CatalogVersionMixin catalogVersion;

    @CommandLine.Parameters(description = { "The Camel YAML source files to parse." },
                            arity = "1..9",
                            paramLabel = "<files>",
                            parameterConsumer = FilesConsumer.class)
    List<String> files = new ArrayList<>();

    public YamlValidateCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // the catalog and schema of the Camel version and runtime asked for; the CLI's own without options
        CatalogVersionMixin.Loaded loaded = catalogVersion.load();
        YamlValidator validator = catalogVersion.yamlValidator(loaded, canonical);

        CamelCatalog camelCatalog = catalog ? loaded.catalog() : null;
        Map<String, List<Error>> reports = new LinkedHashMap<>();
        for (String n : files) {
            if (matchFile(n)) {
                var report = new ArrayList<>(validator.validate(new File(n)));
                if (camelCatalog != null && report.isEmpty()) {
                    // the schema is fine: check what the schema cannot, endpoint URIs and simple expressions
                    // (CAMEL-24698), the same checks the MCP tools do before writing a file
                    String content = Files.readString(new File(n).toPath());
                    for (String msg : SourceValidator.validateYamlCatalog(content, camelCatalog)) {
                        report.add(catalogError(msg));
                    }
                    if (report.isEmpty()) {
                        File parent = new File(n).getAbsoluteFile().getParentFile();
                        var declared = SourceValidator.BeanDeclarations.scan(parent != null ? parent.toPath() : null,
                                new File(n).getName());
                        for (String msg : SourceValidator.validateYamlBeanRefs(content, declared, camelCatalog)) {
                            report.add(catalogError(msg));
                        }
                        if (parent != null) {
                            for (String msg : SourceValidator.validateResourceRefs(content, parent.toPath())) {
                                report.add(catalogError(msg));
                            }
                        }
                    }
                }
                reports.put(n, report);
            } else {
                printer().println("WARN: Skipping non-YAML file: " + n);
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

    static Error catalogError(String message) {
        return Error.builder()
                .messageKey("catalog")
                .format(new MessageFormat("{0}"))
                .arguments(message)
                .build();
    }

    private static boolean matchFile(String name) {
        String no = FileUtil.onlyName(name).toLowerCase(Locale.ROOT);
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

    private static int errorCounts(Map<String, List<Error>> reports) {
        int count = 0;
        for (List<Error> list : reports.values()) {
            count += list.size();
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
