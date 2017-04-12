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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.camel.generator.swagger.RestDslGenerator;
import org.apache.camel.generator.swagger.RestDslSourceCodeGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "generate", inheritByDefault = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    @Parameter
    private String className;

    @Parameter
    private String destinationGenerator;

    @Parameter
    private String indent;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/restdsl-swagger", required = true)
    private String outputDirectory;

    @Parameter
    private String packageName;

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.basedir}/src/spec/swagger.json", required = true)
    private String specificationUri;

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

    DestinationGenerator createDestinationGenerator() throws MojoExecutionException {
        final Class<DestinationGenerator> destinationGeneratorClass;

        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        final URL outputDirectory;
        try {
            outputDirectory = new File(project.getBuild().getOutputDirectory()).toURI().toURL();
        } catch (final MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        final URL[] withOutput = new URL[] {outputDirectory};

        try (URLClassLoader classLoader = new URLClassLoader(withOutput, contextClassLoader)) {
            @SuppressWarnings("unchecked")
            final Class<DestinationGenerator> tmp = (Class) classLoader.loadClass(destinationGenerator);

            if (!DestinationGenerator.class.isAssignableFrom(tmp)) {
                throw new MojoExecutionException("The given destinationGenerator class (" + destinationGenerator
                    + ") does not implement " + DestinationGenerator.class.getName() + " interface.");
            }

            destinationGeneratorClass = tmp;
        } catch (final ClassNotFoundException | IOException e) {
            throw new MojoExecutionException(
                "The given destinationGenerator class (" + destinationGenerator
                    + ") cannot be loaded, make sure that it is present in the COMPILE classpath scope of the project",
                e);
        }

        final DestinationGenerator destinationGeneratorObject;
        try {
            destinationGeneratorObject = destinationGeneratorClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new MojoExecutionException(
                "The given destinationGenerator class (" + destinationGenerator
                    + ") cannot be instantiated, make sure that it is declared as public and that all dependencies are present on the COMPILE classpath scope of the project",
                e);
        }
        return destinationGeneratorObject;
    }

}
