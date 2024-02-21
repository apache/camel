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
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.jakarta.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.jakarta.types.ObjectSchema;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.maven.AbstractSalesforceMojoTest.setup;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaMojoManualIT {

    @TempDir
    public Path temp;

    @Test
    public void testExecuteJsonSchema() throws Exception {
        final SchemaMojo mojo = new SchemaMojo();
        setup(mojo);

        mojo.includes = new String[] { "Account" };
        mojo.outputDirectory = temp.toFile();
        mojo.jsonSchemaFilename = "test-schema.json";
        mojo.jsonSchemaId = JsonUtils.DEFAULT_ID_PREFIX;

        // generate code
        mojo.execute();

        // validate generated schema
        final File schemaFile = mojo.outputDirectory.toPath().resolve("test-schema.json").toFile();
        assertTrue(schemaFile.exists(), "Output file was not created");
        final ObjectMapper objectMapper = JsonUtils.createObjectMapper();
        final JsonSchema jsonSchema = objectMapper.readValue(schemaFile, JsonSchema.class);
        assertTrue(jsonSchema.isObjectSchema() && !((ObjectSchema) jsonSchema).getOneOf().isEmpty(),
                "Expected root JSON schema with oneOf element");
    }

}
