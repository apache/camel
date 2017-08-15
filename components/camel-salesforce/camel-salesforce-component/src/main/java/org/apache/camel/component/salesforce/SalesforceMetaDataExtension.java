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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.CamelContext;
import org.apache.camel.component.extension.metadata.AbstractMetaDataExtension;
import org.apache.camel.component.extension.metadata.MetaDataBuilder;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient.ResponseCallback;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.salesforce.SalesforceClientTemplate.invoke;

public class SalesforceMetaDataExtension extends AbstractMetaDataExtension {

    private static final String OBJECT_TYPE = "objectType";

    public enum Scope implements MetaDataOperation {
        OBJECT(
            (client, params) -> callback -> client.getDescription(stringParam(params, OBJECT_TYPE).get(), callback)) {
            @Override
            public MetaData map(final CamelContext camelContext, final InputStream stream) throws IOException {
                final SObjectDescription description = MAPPER.readerFor(SObjectDescription.class).readValue(stream);

                final List<Map<String, Object>> fields = description.getFields().stream()
                    .map(o -> mapOf("field", o.getName(), "type", o.getType())).collect(Collectors.toList());

                final JsonNode payload = MAPPER.valueToTree(fields);

                return MetaDataBuilder.on(camelContext)//
                    .withAttribute(MetaData.CONTENT_TYPE, "application/json")//
                    .withAttribute(MetaData.JAVA_TYPE, JsonNode.class)//
                    .withAttribute("sObjectName", description.getName())//
                    .withAttribute("label", description.getLabel())//
                    .withPayload(payload).build();
            }
        },
        OBJECT_TYPES((client, params) -> callback -> client.getGlobalObjects(callback)) {
            @Override
            public MetaData map(final CamelContext camelContext, final InputStream stream) throws IOException {
                final GlobalObjects globalObjects = MAPPER.readerFor(GlobalObjects.class).readValue(stream);

                final List<Map<String, Object>> objects = globalObjects.getSobjects().stream()
                    .map(o -> mapOf("sObjectName", o.getName(), "label", o.getLabel())).collect(Collectors.toList());

                final JsonNode payload = MAPPER.valueToTree(objects);

                return MetaDataBuilder.on(camelContext)//
                    .withAttribute(MetaData.CONTENT_TYPE, "application/json")//
                    .withAttribute(MetaData.JAVA_TYPE, JsonNode.class)//
                    .withPayload(payload).build();
            }
        };

        private final BiFunction<RestClient, Map<String, Object>, Consumer<ResponseCallback>> method;

        private Scope(final BiFunction<RestClient, Map<String, Object>, Consumer<ResponseCallback>> method) {
            this.method = method;
        }

        public static Scope valueOf(final Object given) {
            if (given instanceof Scope) {
                return (Scope) given;
            }

            if (given instanceof String) {
                return Scope.valueOf((String) given);
            }

            return Scope.valueOf(String.valueOf(given));
        }
    }

    interface MetaDataOperation {

        MetaData map(CamelContext camelContext, InputStream stream) throws IOException;
    }

    private static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    @Override
    public Optional<MetaData> meta(final Map<String, Object> parameters) {
        final Optional<String> objectType = stringParam(parameters, OBJECT_TYPE);

        final Scope scope = objectType.map(v -> Scope.OBJECT).orElse(Scope.OBJECT_TYPES);

        final MetaData metaData = metaInternal(scope, parameters);

        return Optional.ofNullable(metaData);
    }

    MetaData metaInternal(final Scope scope, final Map<String, Object> parameters) {
        final CamelContext camelContext = getCamelContext();

        try {
            return invoke(camelContext, parameters,
                client -> fetchMetadata(camelContext, scope.method.apply(client, parameters), scope::map));
        } catch (final Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    static MetaData fetchMetadata(final CamelContext camelContext, final Consumer<ResponseCallback> restMethod,
        final MetaDataOperation callback) {
        final CompletableFuture<MetaData> ret = new CompletableFuture<>();

        restMethod.accept((response, exception) -> {
            if (exception != null) {
                ret.completeExceptionally(exception);
            } else {
                try {
                    ret.complete(callback.map(camelContext, response));
                } catch (final IOException e) {
                    ret.completeExceptionally(e);
                }
            }
        });

        try {
            return ret.get();
        } catch (InterruptedException | ExecutionException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    static Map<String, Object> mapOf(final String k1, final Object v1, final String k2, final Object v2) {
        final Map<String, Object> ret = new HashMap<>(2);

        ret.put(k1, v1);
        ret.put(k2, v2);

        return ret;
    }

    static Optional<String> stringParam(final Map<String, Object> params, final String name) {
        final Object value = params.get(name);

        if (value == null) {
            return Optional.empty();
        }

        if (value instanceof String) {
            return Optional.of((String) value);
        }

        throw new IllegalArgumentException("Expecting parameter `" + name + "` to be of String type, got: " + value);
    }
}
