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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import org.apache.camel.CamelContext;
import org.apache.camel.component.extension.metadata.AbstractMetaDataExtension;
import org.apache.camel.component.extension.metadata.MetaDataBuilder;
import org.apache.camel.component.mongodb.conf.ConnectionParamsConfiguration;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.CastUtils.cast;

public class MongoDBMetaExtension extends AbstractMetaDataExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBMetaExtension.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    public MongoDBMetaExtension() {
        this(null);
    }

    public MongoDBMetaExtension(CamelContext context) {
        super(context);
    }

    @Override
    public Optional<MetaData> meta(Map<String, Object> parameters) {
        Map<String, String> textParameters = cast(parameters);
        LOGGER.debug("Fetching mongodb meta information with params: {}", textParameters);

        ConnectionParamsConfiguration mongoConf = new ConnectionParamsConfiguration(textParameters);
        ConnectionString connectionString = new ConnectionString(mongoConf.getMongoClientURI());

        JsonNode collectionInfoRoot;
        try (MongoClient mongoConnection = MongoClients.create(connectionString)) {
            Document collectionInfo = mongoConnection.getDatabase(textParameters.get("database"))
                    .listCollections()
                    .filter(Filters.eq("name", textParameters.get("collection")))
                    .first();
            LOGGER.debug("Collection info: {}", collectionInfo);
            if (collectionInfo == null) {
                LOGGER.error(
                        "Cannot read information for collection {}.{}",
                        textParameters.get("database"),
                        textParameters.get("collection"));
                return Optional.empty();
            }
            String collectionInfoJson = collectionInfo.toJson();
            collectionInfoRoot = objectMapper.readTree(collectionInfoJson.replace("bsonType", "type"));
        } catch (IOException e) {
            LOGGER.error("Error occurred while reading schema information", e);
            return Optional.empty();
        }

        JsonNode schemaRoot = collectionInfoRoot.path("options").path("validator").path("$jsonSchema");

        if (!schemaRoot.isMissingNode()) {
            ObjectNode root = (ObjectNode) schemaRoot;
            root.put("$schema", "http://json-schema.org/schema#");
            root.put("id", String.format("urn:jsonschema:%s:%s:%s)",
                    "org:apache:camel:component:mongodb",
                    textParameters.get("database"),
                    textParameters.get("collection")));

            return Optional.of(
                    MetaDataBuilder.on(getCamelContext())
                            .withAttribute(MetaData.CONTENT_TYPE, "application/schema+json")
                            .withAttribute(MetaData.JAVA_TYPE, JsonNode.class)
                            .withPayload(root)
                            .build()
            );
        } else {
            LOGGER.warn("Cannot retrieve info for : {}.{} collection. Likely the collection has not been provided with a validator",
                    textParameters.get("database"),
                    textParameters.get("collection"));
            return Optional.empty();
        }
    }
}
