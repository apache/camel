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
package org.apache.camel.maven.component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.maven.packaging.AbstractGenerateMojo;
import org.apache.camel.maven.packaging.EndpointSchemaGeneratorMojo;
import org.apache.camel.maven.packaging.GenerateConfigurerMojo;
import org.apache.camel.maven.packaging.GenerateEndpointUriFactoryMojo;
import org.apache.camel.maven.packaging.GenerateInvokeOnHeaderMojo;
import org.apache.camel.maven.packaging.PackageJandexMojo;
import org.apache.camel.maven.packaging.PrepareComponentMojo;
import org.apache.camel.maven.packaging.SpiGeneratorMojo;
import org.apache.camel.maven.packaging.TypeConverterLoaderGeneratorMojo;
import org.apache.camel.maven.packaging.ValidateComponentMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * To be used by 3rd party Camel component developers to generate metadata.
 */
@Mojo(name = "generate", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateComponentMojo extends AbstractGenerateMojo {

    /**
     * The output directory for generated java source code
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    /**
     * The output directory for generated resource source code
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject currentProject;

    @Component
    protected MavenProjectHelper currentProjectHelper;
    @Component
    protected BuildContext currentBuildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.project = currentProject;
        this.projectHelper = currentProjectHelper;
        this.buildContext = currentBuildContext;

        super.execute();
    }

    @Override
    protected void doExecute() throws MojoFailureException, MojoExecutionException {
        // do not sync pom file for this goal as we are standalone
        project.setContextValue("syncPomFile", "false");

        Map<String, Object> parameters = new HashMap<>();
        if (sourcesOutputDir != null) {
            parameters.put("sourcesOutputDir", sourcesOutputDir);
        }
        if (resourcesOutputDir != null) {
            parameters.put("resourcesOutputDir", resourcesOutputDir);
        }

        // jandex
        invoke(PackageJandexMojo.class, parameters);
        // generate-type-converter-loader
        invoke(TypeConverterLoaderGeneratorMojo.class, parameters);
        // generate-spi
        invoke(SpiGeneratorMojo.class, parameters);
        // generate-configurer
        invoke(GenerateConfigurerMojo.class, parameters);
        // generate-endpoint-schema
        invoke(EndpointSchemaGeneratorMojo.class, parameters);
        // generate endpoint-uri-factory
        invoke(GenerateEndpointUriFactoryMojo.class, parameters);
        // generate invoke-on-header
        invoke(GenerateInvokeOnHeaderMojo.class, parameters);
        // prepare-components
        invoke(PrepareComponentMojo.class, parameters);
        // validate-components
        invoke(ValidateComponentMojo.class, parameters);
    }

}
