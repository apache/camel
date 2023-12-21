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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import org.apache.camel.CamelContext;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine;

import static org.openapitools.codegen.CodegenConstants.GENERATE_MODELS;
import static org.openapitools.codegen.CodegenConstants.SERIALIZABLE_MODEL;

@CommandLine.Command(name = "rest", description = "Generate REST DSL source code from OpenApi specification")
public class CodeRestGenerator extends CamelCommand {

    @CommandLine.Option(names = { "-i", "--input" }, required = true, description = "OpenApi specification file name")
    private String input;
    @CommandLine.Option(names = { "-o", "--output" }, description = "Output REST DSL file name")
    private String output;
    @CommandLine.Option(names = { "-t", "--type" }, description = "REST DSL type (YAML or XML)", defaultValue = "yaml")
    private String type;
    @CommandLine.Option(names = { "-r", "--routes" }, description = "Generate routes (only in YAML)")
    private boolean generateRoutes;
    @CommandLine.Option(names = { "-d", "--dto" }, description = "Generate Java Data Objects")
    private boolean generateDataObjects;
    @CommandLine.Option(names = { "-run", "--runtime" }, description = "Runtime (quarkus, or spring-boot)",
                        defaultValue = "quarkus")
    private String runtime;
    @CommandLine.Option(names = { "-p", "--package" }, description = "Package for generated Java models",
                        defaultValue = "model")
    private String packageName;

    public CodeRestGenerator(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        final ObjectNode node = input.endsWith("json") ? readNodeFromJson() : readNodeFromYaml();
        OpenApi30Document document = (OpenApi30Document) Library.readDocument(node);
        Configurator.setRootLevel(Level.OFF);
        try (CamelContext context = new DefaultCamelContext()) {
            String text = null;
            if ("yaml".equalsIgnoreCase(type)) {
                text = RestDslGenerator.toYaml(document).generate(context, generateRoutes);
            } else if ("xml".equalsIgnoreCase(type)) {
                text = RestDslGenerator.toXml(document).generate(context);
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

    private ObjectNode readNodeFromJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        return (ObjectNode) mapper.readTree(Paths.get(input).toFile());
    }

    private ObjectNode readNodeFromYaml() throws FileNotFoundException {
        final ObjectMapper mapper = new ObjectMapper();
        Yaml loader = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<?, ?> map = loader.load(new FileInputStream(Paths.get(input).toFile()));
        return mapper.convertValue(map, ObjectNode.class);
    }

    private void generateDto() throws IOException {
        final String code = "code";
        final String generatorName = "quarkus".equals(runtime) ? "jaxrs-spec" : "java-camel";
        final String library = "quarkus".equals(runtime) ? "quarkus" : "spring-boot";
        File output = Files.createTempDirectory("gendto").toFile();

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(generatorName)
                .setLibrary(library)
                .setInputSpec(input)
                .setModelPackage(packageName)
                .setAdditionalProperties(
                        Map.of(
                                SERIALIZABLE_MODEL, "false",
                                "useJakartaEe", "false",
                                "useSwaggerAnnotations", "false",
                                GENERATE_MODELS, "true",
                                "generatePom", "false",
                                "generateApis", "false",
                                "sourceFolder", code))
                .setOutputDir(output.getAbsolutePath());

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        new DefaultGenerator().opts(clientOptInput).generate();
        File generated = new File(Paths.get(output.getAbsolutePath(), code, packageName).toUri());
        generated.renameTo(new File(packageName));
    }
}
