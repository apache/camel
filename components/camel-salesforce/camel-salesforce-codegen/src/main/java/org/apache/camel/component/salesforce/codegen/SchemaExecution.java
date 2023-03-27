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

package org.apache.camel.component.salesforce.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaExecution extends AbstractSalesforceExecution {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaExecution.class.getName());

    String excludePattern;

    String[] excludes;

    String includePattern;

    String[] includes;

    String jsonSchemaFilename;

    String jsonSchemaId;

    File outputDirectory;

    @Override
    protected void executeWithClient() throws Exception {
        getLog().info("Generating JSON Schema...");

        final ObjectDescriptions descriptions = new ObjectDescriptions(
                getRestClient(), getResponseTimeout(), includes, includePattern, excludes, excludePattern, getLog());

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
                throw new RuntimeException("Unable to generate JSON Schema types for: " + description.getName(), e);
            }
        }

        final Path schemaFilePath = outputDirectory.toPath().resolve(jsonSchemaFilename);
        try {
            Files.write(schemaFilePath,
                    JsonUtils.getJsonSchemaString(schemaObjectMapper, allSchemas, jsonSchemaId)
                            .getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new RuntimeException("Unable to generate JSON Schema source file: " + schemaFilePath, e);
        }

        getLog().info(
                String.format("Successfully generated %s JSON Types in file %s", descriptions.count() * 2, schemaFilePath));
    }

    @Override
    protected Logger getLog() {
        return LOG;
    }

    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }

    public void setIncludePattern(String includePattern) {
        this.includePattern = includePattern;
    }

    public void setIncludes(String[] includes) {
        this.includes = includes;
    }

    public void setJsonSchemaFilename(String jsonSchemaFilename) {
        this.jsonSchemaFilename = jsonSchemaFilename;
    }

    public void setJsonSchemaId(String jsonSchemaId) {
        this.jsonSchemaId = jsonSchemaId;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
}
