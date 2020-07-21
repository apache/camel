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
package org.apache.camel.component.mongodb.meta;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ValidationOptions;
import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.mongodb.AbstractMongoDbTest;
import org.apache.camel.component.mongodb.MongoDbComponent;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MongoDbMetaExtensionTest extends AbstractMongoDbTest {
    // We simulate the presence of an authenticated user
    @BeforeEach
    public void createAuthorizationUser() {
        super.createAuthorizationUser();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected MongoDbComponent getComponent() {
        return context().getComponent(SCHEME, MongoDbComponent.class);
    }

    @Test
    public void testValidMetaData() throws Exception {
        // When
        final String database = "test";
        final String collection = "validatedCollection";
        MongoDbComponent component = this.getComponent();
        // Given
        Document jsonSchema = Document.parse("{ \n"
                + "      bsonType: \"object\", \n"
                + "      required: [ \"name\", \"surname\", \"email\" ], \n"
                + "      properties: { \n"
                + "         name: { \n"
                + "            bsonType: \"string\", \n"
                + "            description: \"required and must be a string\" }, \n"
                + "         surname: { \n"
                + "            bsonType: \"string\", \n"
                + "            description: \"required and must be a string\" }, \n"
                + "         email: { \n"
                + "            bsonType: \"string\", \n"
                + "            pattern: \"^.+@.+$\", \n"
                + "            description: \"required and must be a valid email address\" }, \n"
                + "         year_of_birth: { \n"
                + "            bsonType: \"int\", \n"
                + "            minimum: 1900, \n"
                + "            maximum: 2018,\n"
                + "            description: \"the value must be in the range 1900-2018\" }, \n"
                + "         gender: { \n"
                + "            enum: [ \"M\", \"F\" ], \n"
                + "            description: \"can be only M or F\" } \n"
                + "      }}");
        ValidationOptions collOptions = new ValidationOptions().validator(Filters.jsonSchema(jsonSchema));
        AbstractMongoDbTest.mongo.getDatabase(database).createCollection(collection,
                new CreateCollectionOptions().validationOptions(collOptions));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("database", database);
        parameters.put("collection", collection);
        parameters.put("host", container.getConnectionAddress());
        parameters.put("user", USER);
        parameters.put("password", PASSWORD);

        MetaDataExtension.MetaData result = component.getExtension(MetaDataExtension.class).get().meta(parameters).orElseThrow(UnsupportedOperationException::new);
        // Then
        assertEquals("application/schema+json", result.getAttribute(MetaDataExtension.MetaData.CONTENT_TYPE));
        assertEquals(JsonNode.class, result.getAttribute(MetaDataExtension.MetaData.JAVA_TYPE));
        assertNotNull(result.getPayload(JsonNode.class));
        assertNotNull(result.getPayload(JsonNode.class).get("properties"));
        assertNotNull(result.getPayload(JsonNode.class).get("$schema"));
        assertEquals("http://json-schema.org/schema#", result.getPayload(JsonNode.class).get("$schema").asText());
        assertNotNull(result.getPayload(JsonNode.class).get("id"));
        assertNotNull(result.getPayload(JsonNode.class).get("type"));
    }

    @Test
    public void testMissingCollection() throws Exception {
        // When
        final String database = "test";
        final String collection = "missingCollection";
        MongoDbComponent component = this.getComponent();
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("database", database);
        parameters.put("collection", collection);
        parameters.put("host", container.getConnectionAddress());
        parameters.put("user", USER);
        parameters.put("password", PASSWORD);

        // Then
        assertThrows(IllegalArgumentException.class, () -> {
            component.getExtension(MetaDataExtension.class).get().meta(parameters).orElseThrow(IllegalArgumentException::new);
        });
    }

    @Test
    public void testMissingParameters() throws Exception {
        // When
        MongoDbComponent component = this.getComponent();
        // Given
        Map<String, Object> parameters = new HashMap<>();

        // Then
        assertThrows(IllegalArgumentException.class, () -> {
            component.getExtension(MetaDataExtension.class).get().meta(parameters).orElseThrow(IllegalArgumentException::new);
        });
    }

    @Test
    public void testNotValidatedCollection() throws Exception {
        // When
        final String database = "test";
        final String collection = "notValidatedCollection";
        MongoDbComponent component = this.getComponent();
        AbstractMongoDbTest.mongo.getDatabase(database).createCollection(collection);
        // Given
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("database", database);
        parameters.put("collection", collection);
        parameters.put("host", container.getConnectionAddress());
        parameters.put("user", USER);
        parameters.put("password", PASSWORD);

        // Then
        assertThrows(UnsupportedOperationException.class, () -> {
            component.getExtension(MetaDataExtension.class).get().meta(parameters).orElseThrow(UnsupportedOperationException::new);
        });
    }

}
