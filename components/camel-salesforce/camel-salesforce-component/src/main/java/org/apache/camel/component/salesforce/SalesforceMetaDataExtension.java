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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.extension.metadata.AbstractMetaDataExtension;
import org.apache.camel.component.extension.metadata.MetaDataBuilder;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;

public class SalesforceMetaDataExtension extends AbstractMetaDataExtension {

    @FunctionalInterface
    interface SchemaMapper {
        JsonSchema map(InputStream stream) throws IOException;
    }

    private static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    @Override
    public Optional<MetaData> meta(final Map<String, Object> parameters) {
        final JsonSchema schema = schemaFor(parameters);

        final MetaData metaData = MetaDataBuilder.on(getCamelContext())//
            .withAttribute(MetaData.CONTENT_TYPE, "application/schema+json")//
            .withAttribute(MetaData.JAVA_TYPE, JsonNode.class)//
            .withPayload(schema).build();

        return Optional.ofNullable(metaData);
    }

    JsonSchema allObjectsSchema(final Map<String, Object> parameters) throws Exception {
        return SalesforceClientTemplate.invoke(getCamelContext(), parameters, client -> fetchAllObjectsSchema(client));
    }

    JsonSchema schemaFor(final Map<String, Object> parameters) {
        try {
            if (parameters.containsKey(SalesforceEndpointConfig.SOBJECT_NAME)) {
                return singleObjectSchema(parameters);
            }

            return allObjectsSchema(parameters);
        } catch (final Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

    }

    JsonSchema singleObjectSchema(final Map<String, Object> parameters) throws Exception {
        return SalesforceClientTemplate.invoke(getCamelContext(), parameters,
        client -> fetchSingleObjectSchema(client, (String)parameters.get(SalesforceEndpointConfig.SOBJECT_NAME)));
    }

    static JsonSchema fetch(final Consumer<ResponseCallback> restMethod, final SchemaMapper callback) {
        final CompletableFuture<JsonSchema> ret = new CompletableFuture<>();

        restMethod.accept((response, headers, exception) -> {
            if (exception != null) {
                ret.completeExceptionally(exception);
            } else {
                try (final InputStream is = response) {
                    ret.complete(callback.map(is));
                } catch (final IOException e) {
                    ret.completeExceptionally(e);
                }
            }
        });

        try {
            return ret.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    static JsonSchema fetchAllObjectsSchema(final RestClient client) {
        return fetch(callback -> client.getGlobalObjects(Collections.emptyMap(), callback), SalesforceMetaDataExtension::mapAllObjectsSchema);
    }

    static JsonSchema fetchSingleObjectSchema(final RestClient client, final String objectName) {
        return fetch(callback -> client.getDescription(objectName, Collections.emptyMap(), callback), SalesforceMetaDataExtension::mapSingleObjectSchema);
    }

    static JsonSchema mapAllObjectsSchema(final InputStream stream) throws IOException {
        final GlobalObjects globalObjects = MAPPER.readerFor(GlobalObjects.class).readValue(stream);

        return JsonUtils.getGlobalObjectsJsonSchemaAsSchema(globalObjects);
    }

    static JsonSchema mapSingleObjectSchema(final InputStream stream) throws IOException {
        final SObjectDescription description = MAPPER.readerFor(SObjectDescription.class).readValue(stream);

        return JsonUtils.getSObjectJsonSchemaAsSchema(description, true);
    }

}
