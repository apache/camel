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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine;

@CommandLine.Command(name = "rest", description = "Generate REST DSL source code from OpenApi specification")
public class CodeRestGenerator extends CamelCommand {

    @CommandLine.Option(names = { "-i", "--input" }, required = true, description = "OpenApi specification file name")
    private String input;
    @CommandLine.Option(names = { "-o", "--output" }, description = "Output REST DSL file name")
    private String output;
    @CommandLine.Option(names = { "-t", "--type" }, description = "REST DSL type (YAML or XML)", defaultValue = "yaml")
    private String type;
    @CommandLine.Option(names = { "-r", "--routes" }, description = "Generate routes (YAML)")
    private boolean generateRoutes;

    public CodeRestGenerator(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        final JsonNode node = input.endsWith("json") ? readNodeFromJson() : readNodeFromYaml();
        OasDocument document = (OasDocument) Library.readDocument(node);
        Configurator.setRootLevel(Level.OFF);
        try (CamelContext context = new LightweightCamelContext()) {
            final String yaml = type.equalsIgnoreCase("yaml")
                    ? RestDslGenerator.toYaml(document).generate(context, generateRoutes)
                    : RestDslGenerator.toXml(document).generate(context);
            if (output == null) {
                System.out.println(yaml);
            } else {
                Files.write(Paths.get(output), yaml.getBytes());
            }
        }
        return 0;
    }

    private JsonNode readNodeFromJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(Paths.get(input).toFile());
    }

    private JsonNode readNodeFromYaml() throws FileNotFoundException {
        final ObjectMapper mapper = new ObjectMapper();
        Yaml loader = new Yaml(new SafeConstructor());
        Map map = loader.load(new FileInputStream(Paths.get(input).toFile()));
        return mapper.convertValue(map, JsonNode.class);
    }
}
