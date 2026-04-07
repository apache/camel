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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import org.apache.camel.CamelContext;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoader;
import org.apache.camel.dsl.yaml.validator.DummyPropertiesParser;
import org.apache.camel.dsl.yaml.validator.DummyTypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.stub.StubBeanRepository;
import org.apache.camel.main.stub.StubDataFormat;
import org.apache.camel.main.stub.StubEipReifier;
import org.apache.camel.main.stub.StubLanguage;
import org.apache.camel.main.stub.StubTransformer;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
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
        int count = 0;
        int errors = 0;
        for (String n : files) {
            if (!matchFile(n)) {
                continue;
            }
            String normalized = normalize(new File(n));
            if (normalized == null) {
                printer().printErr("Error normalizing: " + n);
                errors++;
                continue;
            }
            if (output != null) {
                Path outPath = Path.of(output);
                if (Files.isDirectory(outPath)) {
                    outPath = outPath.resolve(Path.of(n).getFileName());
                }
                Files.writeString(outPath, normalized);
                count++;
            } else {
                printer().println(normalized);
            }
        }
        if (output != null) {
            printer().println("Normalized " + count + " file(s) to " + output);
        }
        return errors > 0 ? 1 : 0;
    }

    private String normalize(File file) throws Exception {
        DefaultRegistry registry = new DefaultRegistry();
        registry.addBeanRepository(new StubBeanRepository("*"));

        try (CamelContext camelContext = new DefaultCamelContext(registry)) {
            camelContext.setAutoStartup(false);
            camelContext.getCamelContextExtension().addContextPlugin(ComponentResolver.class,
                    (name, context) -> new StubComponent());
            camelContext.getCamelContextExtension().addContextPlugin(DataFormatResolver.class,
                    (name, context) -> new StubDataFormat());
            camelContext.getCamelContextExtension().addContextPlugin(LanguageResolver.class,
                    (name, context) -> new StubLanguage());
            camelContext.getCamelContextExtension().addContextPlugin(TransformerResolver.class,
                    (name, context) -> new StubTransformer());

            PropertiesComponent pc = (PropertiesComponent) camelContext.getPropertiesComponent();
            pc.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
            pc.addInitialProperty("camel.component.properties.ignoreMissingLocation", "true");
            pc.setPropertiesParser(new DummyPropertiesParser(camelContext));

            DummyTypeConverter ec = new DummyTypeConverter();
            var tcr = camelContext.getTypeConverterRegistry();
            tcr.setTypeConverterExists(TypeConverterExists.Override);
            tcr.addTypeConverter(Integer.class, String.class, ec);
            tcr.addTypeConverter(Long.class, String.class, ec);
            tcr.addTypeConverter(Double.class, String.class, ec);
            tcr.addTypeConverter(Float.class, String.class, ec);
            tcr.addTypeConverter(Byte.class, String.class, ec);
            tcr.addTypeConverter(Boolean.class, String.class, ec);
            tcr.addFallbackTypeConverter(ec, false);

            StubEipReifier.registerStubEipReifiers(camelContext);

            camelContext.start();

            // load YAML routes
            try (YamlRoutesBuilderLoader loader = new YamlRoutesBuilderLoader()) {
                loader.setCamelContext(camelContext);
                loader.start();
                var rb = loader.doLoadRouteBuilder(
                        ResourceHelper.fromString(file.getName(), Files.readString(file.toPath())));
                camelContext.addRoutes(rb);
            }

            // get loaded route definitions
            Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
            List<RouteDefinition> routes = model.getRouteDefinitions();

            if (routes.isEmpty()) {
                return "";
            }

            // dump as YAML in canonical form
            var dumper = PluginHelper.getModelToYAMLDumper(camelContext);
            RoutesDefinition rd = new RoutesDefinition();
            rd.setRoutes(routes);
            return dumper.dumpModelAsYaml(camelContext, rd, false, false, false, false);
        }
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

    static class FilesConsumer extends CamelCommand.ParameterConsumer<YamlNormalizeCommand> {
        @Override
        protected void doConsumeParameters(Stack<String> args, YamlNormalizeCommand cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
