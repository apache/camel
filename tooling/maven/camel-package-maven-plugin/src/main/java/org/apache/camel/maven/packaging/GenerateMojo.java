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
 * Used by Apache Camel project itself - do NOT use as end user.
 */
@Mojo(name = "generate", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateMojo extends AbstractGenerateMojo {

    protected void doExecute() throws MojoFailureException, MojoExecutionException {
        // jandex
        invoke(PackageJandexMojo.class);
        // generate model json schema
        invoke(SchemaGeneratorMojo.class);
        // generate-type-converter-loader
        invoke(TypeConverterLoaderGeneratorMojo.class);
        // generate-spi
        invoke(SpiGeneratorMojo.class);
        // generate-jaxb-list
        invoke(PackageJaxbMojo.class);
        // generate-eips-list
        invoke(PackageModelMojo.class);
        // generate-endpoint-schema
        invoke(EndpointSchemaGeneratorMojo.class);
        // generate endpoint-uri-factory
        invoke(GenerateEndpointUriFactoryMojo.class);
        // generate configurer
        invoke(GenerateConfigurerMojo.class);
        // generate invoke-on-header
        invoke(GenerateInvokeOnHeaderMojo.class);
        // generate data-type-transformer
        invoke(GenerateDataTypeTransformerMojo.class);
        // prepare-components
        invoke(PrepareComponentMojo.class);
        // prepare-main
        invoke(PrepareCamelMainMojo.class);
        // prepare-main-doc
        invoke(PrepareCamelMainDocMojo.class);
        // generate-xml-parser
        invoke(ModelXmlParserGeneratorMojo.class);
        // generate-legal
        invoke(PackageLegalMojo.class);
        // validate-components
        invoke(ValidateComponentMojo.class);
        // update-readme
        invoke(UpdateReadmeMojo.class);
    }

}
