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
package org.apache.camel.maven.packaging;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * To be used by 3rd party Camel component developers to generate metadata.
 */
@Mojo(name = "generate-component", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateComponentMojo extends AbstractGenerateMojo {

    protected void doExecute() throws MojoFailureException, MojoExecutionException {

        // do not sync pom file for this goal as we are standalone
        project.setContextValue("syncPomFile", "false");

        // jandex
        invoke(PackageJandexMojo.class);
        // generate-type-converter-loader
        invoke(TypeConverterLoaderGeneratorMojo.class);
        // generate-spi
        invoke(SpiGeneratorMojo.class);
        // generate-endpoint-schema
        invoke(EndpointSchemaGeneratorMojo.class);
        // prepare-components
        invoke(PrepareComponentMojo.class);
        // validate-components
        invoke(ValidateComponentMojo.class);
    }

}
