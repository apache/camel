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

package org.apache.camel.dsl.jbang.core.commands.generate;

import static org.openapitools.codegen.CodegenConstants.GENERATE_MODELS;
import static org.openapitools.codegen.CodegenConstants.SERIALIZABLE_MODEL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.RuntimeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.RuntimeTypeConverter;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import picocli.CommandLine;

@CommandLine.Command(name = "rest", description = "Generate REST DSL source code from OpenApi specification")
public class CodeRestGenerator extends CamelCommand {

    public static class OpenApiVersionCompletionCandidates implements Iterable<String> {

        public OpenApiVersionCompletionCandidates() {}

        @Override
        public Iterator<String> iterator() {
            return List.of("3.0", "3.1").iterator();
        }
    }

    public static class OpenApiTypeCompletionCandidates implements Iterable<String> {

        public OpenApiTypeCompletionCandidates() {}

        @Override
        public Iterator<String> iterator() {
            return List.of("xml", "yaml").iterator();
        }
    }

    @CommandLine.Option(
            names = {"--input"},
            required = true,
            description = "OpenApi specification file name")
    private String input;

    @CommandLine.Option(
            names = {"--output"},
            description = "Output REST DSL file name")
    private String output;

    @CommandLine.Option(
            names = {"--type"},
            description = "REST DSL type (YAML or XML)",
            defaultValue = "yaml",
            completionCandidates = OpenApiTypeCompletionCandidates.class)
    private String type;

    @CommandLine.Option(
            names = {"--routes"},
            description = "Generate routes (only in YAML)")
    private boolean generateRoutes;

    @CommandLine.Option(
            names = {"--dto"},
            description = "Generate Java Data Objects")
    private boolean generateDataObjects;

    @CommandLine.Option(
            names = {"--runtime"},
            completionCandidates = RuntimeCompletionCandidates.class,
            converter = RuntimeTypeConverter.class,
            defaultValue = "quarkus",
            description = "Runtime (${COMPLETION-CANDIDATES})")
    RuntimeType runtime = RuntimeType.quarkus;

    @CommandLine.Option(
            names = {"--package"},
            description = "Package for generated Java models",
            defaultValue = "model")
    private String packageName;

    @CommandLine.Option(
            names = {"--openapi-version"},
            description = "Openapi specification 3.0 or 3.1",
            defaultValue = "3.0",
            completionCandidates = OpenApiVersionCompletionCandidates.class)
    private String openApiVersion = "3.0";

    public CodeRestGenerator(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // validate that the input file exists
        File f = new File(input);
        if (!f.exists() && !f.isFile()) {
            printer().println("Error: Input file " + input + " does not exist");
            return 1;
        }

        OpenAPI doc;

        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        doc = parser.read(input);

        Configurator.setRootLevel(Level.OFF);
        try (CamelContext context = new DefaultCamelContext()) {
            String text = null;
            if ("yaml".equalsIgnoreCase(type)) {
                text = RestDslGenerator.toYaml(doc)
                        .withDtoPackageName(generateDataObjects ? packageName : null)
                        .generate(context, generateRoutes);
            } else if ("xml".equalsIgnoreCase(type)) {
                text = RestDslGenerator.toXml(doc)
                        .withDtoPackageName(generateDataObjects ? packageName : null)
                        .generate(context);
            }
            if (text != null) {
                if (output == null) {
                    printer().println(text);
                } else {
                    Files.write(Paths.get(output), text.getBytes());
                }
            }
        }
        if (generateDataObjects) {
            generateDto();
        }
        return 0;
    }

    private void generateDto() throws IOException {
        final String code = "code";
        final String generatorName = RuntimeType.quarkus.equals(runtime) ? "jaxrs-spec" : "java-camel";
        final String library = RuntimeType.quarkus.equals(runtime) ? "quarkus" : "spring-boot";
        File output = Files.createTempDirectory("gendto").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(generatorName)
                .setLibrary(library)
                .setInputSpec(input)
                .setModelPackage(packageName)
                .setAdditionalProperties(Map.of(
                        SERIALIZABLE_MODEL,
                        "false",
                        "useJakartaEe",
                        "false",
                        "useSwaggerAnnotations",
                        "false",
                        GENERATE_MODELS,
                        "true",
                        "generatePom",
                        "false",
                        "generateApis",
                        "false",
                        "sourceFolder",
                        code))
                .setOutputDir(output.getAbsolutePath());

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        new DefaultGenerator().opts(clientOptInput).generate();
        File generated =
                new File(Paths.get(output.getAbsolutePath(), code, packageName).toUri());
        generated.renameTo(new File(packageName));
    }
}
