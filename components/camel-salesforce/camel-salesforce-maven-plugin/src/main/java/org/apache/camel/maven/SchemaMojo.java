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
package org.apache.camel.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;

import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.maven.plugin.MojoExecutionException;
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
     * Location of generated JSON schema files, defaults to
     * target/generated-sources/camel-salesforce.
     */
    @Parameter(property = "camelSalesforce.outputDirectory",
        defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    File outputDirectory;

    @Override
    protected void executeWithClient(final RestClient client) throws MojoExecutionException {
        getLog().info("Generating JSON Schema...");

        final ObjectDescriptions descriptions = new ObjectDescriptions(client, getResponseTimeout(), includes,
            includePattern, excludes, excludePattern, getLog());

        // generate JSON schema for every object description
        final ObjectMapper schemaObjectMapper = JsonUtils.createSchemaObjectMapper();
        final Set<JsonSchema> allSchemas = new HashSet<>();
        for (final SObjectDescription description : descriptions.fetched()) {
            if (Defaults.IGNORED_OBJECTS.contains(description.getName())) {
                continue;
            }
            try {
                allSchemas.addAll(JsonUtils.getSObjectJsonSchema(schemaObjectMapper, description, jsonSchemaId, true));
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to generate JSON Schema types for: " + description.getName(),
                    e);
            }
        }

        final Path schemaFilePath = outputDirectory.toPath().resolve(jsonSchemaFilename);
        try {
            Files.write(schemaFilePath,
                JsonUtils.getJsonSchemaString(schemaObjectMapper, allSchemas, jsonSchemaId).getBytes("UTF-8"));
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to generate JSON Schema source file: " + schemaFilePath, e);
        }

        getLog().info(
            String.format("Successfully generated %s JSON Types in file %s", descriptions.count() * 2, schemaFilePath));
    }

}
