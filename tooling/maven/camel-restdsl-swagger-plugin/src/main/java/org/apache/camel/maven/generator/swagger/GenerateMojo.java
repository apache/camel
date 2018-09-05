/**
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
package org.apache.camel.maven.generator.swagger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.generator.swagger.RestDslSourceCodeGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate", inheritByDefault = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateMojo extends AbstractGenerateMojo {

    @Parameter
    private String className;

    @Parameter
    private String indent;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/restdsl-swagger", required = true)
    private String outputDirectory;

    @Parameter
    private String packageName;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }

        final SwaggerParser swaggerParser = new SwaggerParser();

        final Swagger swagger = swaggerParser.read(specificationUri);

        if (swagger == null) {
            throw new MojoExecutionException("Unable to generate REST DSL Swagger sources from specification: "
                + specificationUri + ", make sure that the specification is available at the given URI");
        }

        final RestDslSourceCodeGenerator<Path> generator = RestDslGenerator.toPath(swagger);

        if (ObjectHelper.isNotEmpty(filterOperation)) {
            generator.withOperationFilter(filterOperation);
        }

        if (ObjectHelper.isNotEmpty(className)) {
            generator.withClassName(className);
        }

        if (indent != null) {
            generator.withIndent(indent.replace("\\t", "\t"));
        }

        if (ObjectHelper.isNotEmpty(packageName)) {
            generator.withPackageName(packageName);
        }

        if (ObjectHelper.isNotEmpty(destinationGenerator)) {
            final DestinationGenerator destinationGeneratorObject = createDestinationGenerator();

            generator.withDestinationGenerator(destinationGeneratorObject);
        }

        final Path outputPath = new File(outputDirectory).toPath();

        try {
            generator.generate(outputPath);
        } catch (final IOException e) {
            throw new MojoExecutionException(
                "Unable to generate REST DSL Swagger sources from specification: " + specificationUri, e);
        }
    }

}
