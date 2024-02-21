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

import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.generator.openapi.DestinationGenerator;
import org.apache.camel.generator.openapi.RestDslGenerator;
import org.apache.camel.generator.openapi.RestDslXmlGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate-xml", inheritByDefault = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateXmlMojo extends AbstractGenerateMojo {

    @Parameter(defaultValue = "false")
    private boolean blueprint;

    @Parameter(defaultValue = "camel-rest.xml", required = true)
    private String fileName;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/restdsl-openapi", required = true)
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }

        OpenApiDocument openapi;
        try {
            openapi = readOpenApiDoc(specificationUri);
        } catch (Exception e1) {
            throw new MojoExecutionException("can't load open api doc from " + specificationUri, e1);
        }

        if (openapi == null) {
            throw new MojoExecutionException(
                    "Unable to generate REST DSL OpenApi sources from specification: "
                                             + specificationUri
                                             + ", make sure that the specification is available at the given URI");
        }

        final RestDslXmlGenerator generator = RestDslGenerator.toXml(openapi);

        if (blueprint) {
            generator.withBlueprint();
        }

        if (ObjectHelper.isNotEmpty(basePath)) {
            generator.withBasePath(basePath);
        }

        if (ObjectHelper.isNotEmpty(filterOperation)) {
            generator.withOperationFilter(filterOperation);
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
            final String xml = generator.generate(camel);

            // ensure output folder is created
            new File(outputDirectory).mkdirs();
            final File out = new File(outputDirectory, fileName);

            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(xml.getBytes());
            }
        } catch (final Exception e) {
            throw new MojoExecutionException(
                    "Unable to generate REST DSL OpenApi sources from specification: " + specificationUri, e);
        }
    }

}
