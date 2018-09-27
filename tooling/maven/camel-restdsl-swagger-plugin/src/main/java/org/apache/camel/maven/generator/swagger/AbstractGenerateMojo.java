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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.generator.swagger.DestinationGenerator;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

abstract class AbstractGenerateMojo extends AbstractMojo {

    @Parameter
    String destinationGenerator;

    @Parameter
    String filterOperation;

    @Parameter(defaultValue = "${project}")
    MavenProject project;

    @Parameter(defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${project.basedir}/src/spec/swagger.json", required = true)
    String specificationUri;

    @Parameter(defaultValue = "2.3.1")
    String swaggerCodegenMavenPluginVersion;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/swagger")
    String modelOutput;

    @Parameter
    String modelPackage;

    @Parameter
    String modelNamePrefix;

    @Parameter
    String modelNameSuffix;

    @Parameter(defaultValue = "true")
    String modelWithXml;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

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

    void generateDto(String language) throws MojoExecutionException {
        getLog().info("Generating DTO classes using io.swagger:swagger-codegen-maven-plugin:" + swaggerCodegenMavenPluginVersion);

        // swagger-codegen-maven-plugin documentation and its supported options
        // https://github.com/swagger-api/swagger-codegen/tree/master/modules/swagger-codegen-maven-plugin

        List<MojoExecutor.Element> elements = new ArrayList<>();
        elements.add(new MojoExecutor.Element("inputSpec", specificationUri));
        elements.add(new MojoExecutor.Element("language", language));
        elements.add(new MojoExecutor.Element("generateApis", "false"));
        elements.add(new MojoExecutor.Element("generateModelTests", "false"));
        elements.add(new MojoExecutor.Element("generateModelDocumentation", "false"));
        elements.add(new MojoExecutor.Element("generateSupportingFiles", "false"));
        if (modelOutput != null) {
            elements.add(new MojoExecutor.Element("output", modelOutput));
        }
        if (modelPackage != null) {
            elements.add(new MojoExecutor.Element("modelPackage", modelPackage));
        }
        if (modelNamePrefix != null) {
            elements.add(new MojoExecutor.Element("modelNamePrefix", modelNamePrefix));
        }
        if (modelNameSuffix != null) {
            elements.add(new MojoExecutor.Element("modelNameSuffix", modelNameSuffix));
        }
        if (modelWithXml != null) {
            elements.add(new MojoExecutor.Element("withXml", modelPackage));
        }

        executeMojo(
            plugin(
                groupId("io.swagger"),
                artifactId("swagger-codegen-maven-plugin"),
                version(swaggerCodegenMavenPluginVersion)
            ),
            goal("generate"),
            configuration(
                elements.toArray(new MojoExecutor.Element[elements.size()])
            ),
            executionEnvironment(
                mavenProject,
                mavenSession,
                pluginManager
            )
        );

    }

}
