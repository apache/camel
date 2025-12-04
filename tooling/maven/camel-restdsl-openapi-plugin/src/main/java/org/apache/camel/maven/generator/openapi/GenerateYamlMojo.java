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

package org.apache.camel.maven.generator.openapi;

import java.io.File;
import java.io.FileOutputStream;

import javax.inject.Inject;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.camel.CamelContext;
import org.apache.camel.generator.openapi.DestinationGenerator;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.generator.openapi.RestDslYamlGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "generate-yaml",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true)
public class GenerateYamlMojo extends AbstractGenerateMojo {

    @Parameter(defaultValue = "camel-rest.yaml", required = true)
    private String fileName;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/restdsl-openapi", required = true)
    private String outputDirectory;

    @Inject
    public GenerateYamlMojo(BuildPluginManager pluginManager) {
        super(pluginManager);
    }

    @Override
    public void execute() throws MojoExecutionException {
        execute(false);
    }

    protected void execute(boolean dto) throws MojoExecutionException {
        if (skip) {
            return;
        }

        OpenAPI openapi = new OpenAPIV3Parser().read(specificationUri);

        if (openapi == null) {
            throw new MojoExecutionException("Unable to generate REST DSL OpenApi sources from specification: "
                    + specificationUri
                    + ", make sure that the specification is available at the given URI");
        }

        final RestDslYamlGenerator generator = RestDslGenerator.toYaml(openapi);

        if (ObjectHelper.isNotEmpty(basePath)) {
            generator.withBasePath(basePath);
        }

        if (ObjectHelper.isNotEmpty(filterOperation)) {
            generator.withOperationFilter(filterOperation);
        }
        if (dto) {
            if (modelPackage != null) {
                generator.withDtoPackageName(modelPackage);
            }
        }

        if (ObjectHelper.isNotEmpty(destinationGenerator)) {
            final DestinationGenerator destinationGeneratorObject = createDestinationGenerator();
            generator.withDestinationGenerator(destinationGeneratorObject);
        } else if (ObjectHelper.isNotEmpty(destinationToSyntax)) {
            generator.withDestinationToSyntax(destinationToSyntax);
        }

        if (restConfiguration) {
            generator.withRestComponent(findAppropriateComponent());
            if (clientRequestValidation) {
                generator.withClientRequestValidation();
            }
            if (ObjectHelper.isNotEmpty(apiContextPath)) {
                generator.withApiContextPath(apiContextPath);
            }
        }

        try {
            final CamelContext camel = new DefaultCamelContext();
            final String yaml = generator.generate(camel);

            // ensure output folder is created
            new File(outputDirectory).mkdirs();
            final File out = new File(outputDirectory, fileName);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(yaml.getBytes());
            }
        } catch (final Exception e) {
            throw new MojoExecutionException(
                    "Unable to generate REST DSL OpenApi sources from specification: " + specificationUri, e);
        }
    }
}
