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
package org.apache.camel.maven;

import java.io.File;

import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.camel.component.salesforce.codegen.SchemaExecution;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "schema", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SchemaMojo extends AbstractSalesforceMojo {

    /**
     * Exclude Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.excludePattern")
    String excludePattern;

    /**
     * Do NOT generate DTOs for these Salesforce SObjects.
     */
    @Parameter
    String[] excludes;

    /**
     * Include Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.includePattern")
    String includePattern;

    /**
     * Names of Salesforce SObject for which DTOs must be generated.
     */
    @Parameter
    String[] includes;

    /**
     * Schema ID for JSON Schema for DTOs.
     */
    @Parameter(property = "camelSalesforce.jsonSchemaFilename", defaultValue = "salesforce-dto-schema.json")
    String jsonSchemaFilename;

    /**
     * Schema ID for JSON Schema for DTOs.
     */
    @Parameter(property = "camelSalesforce.jsonSchemaId", defaultValue = JsonUtils.DEFAULT_ID_PREFIX)
    String jsonSchemaId;

    /**
     * Location of generated JSON schema files, defaults to target/generated-sources/camel-salesforce.
     */
    @Parameter(property = "camelSalesforce.outputDirectory",
               defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    File outputDirectory;

    private final SchemaExecution execution = new SchemaExecution();

    @Override
    protected AbstractSalesforceExecution getSalesforceExecution() {
        return execution;
    }

    @Override
    protected void setup() {
        super.setup();
        execution.setExcludes(excludes);
        execution.setExcludePattern(excludePattern);
        execution.setIncludes(includes);
        execution.setIncludePattern(includePattern);
        execution.setJsonSchemaId(jsonSchemaId);
        execution.setJsonSchemaFilename(jsonSchemaFilename);
        execution.setOutputDirectory(outputDirectory);
        execution.setup();
    }
}
