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
import java.io.FileOutputStream;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.apache.camel.CamelContext;
import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.generator.swagger.RestDslXmlGenerator;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate-xml", inheritByDefault = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateXmlMojo extends AbstractGenerateMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/restdsl-swagger", required = true)
    private String outputDirectory;

    @Parameter(defaultValue = "camel-rest.xml", required = true)
    private String fileName;

    @Parameter(defaultValue = "false")
    private boolean blueprint;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        final SwaggerParser swaggerParser = new SwaggerParser();

        final Swagger swagger = swaggerParser.read(specificationUri);

        if (swagger == null) {
            throw new MojoExecutionException("Unable to generate REST DSL Swagger sources from specification: "
                + specificationUri + ", make sure that the specification is available at the given URI");
        }

        final RestDslXmlGenerator generator = RestDslGenerator.toXml(swagger);

        if (blueprint) {
            generator.withBlueprint();
        }

        if (ObjectHelper.isNotEmpty(filterOperation)) {
            generator.withOperationFilter(filterOperation);
        }

        if (ObjectHelper.isNotEmpty(destinationGenerator)) {
            final DestinationGenerator destinationGeneratorObject = createDestinationGenerator();

            generator.withDestinationGenerator(destinationGeneratorObject);
        }

        try {
            CamelContext camel = new DefaultCamelContext();
            String xml = generator.generate(camel);

            // ensure output folder is created
            new File(outputDirectory).mkdirs();
            File out = new File(outputDirectory, fileName);

            FileOutputStream fos = new FileOutputStream(out);
            fos.write(xml.getBytes());
            fos.close();

        } catch (final Exception e) {
            throw new MojoExecutionException(
                "Unable to generate REST DSL Swagger sources from specification: " + specificationUri, e);
        }
    }

}
