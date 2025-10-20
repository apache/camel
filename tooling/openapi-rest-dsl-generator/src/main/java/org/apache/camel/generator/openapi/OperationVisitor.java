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
package org.apache.camel.generator.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OperationVisitor<T> {

    private static final Logger LOG = LoggerFactory.getLogger(OperationVisitor.class);

    private final OpenAPI openAPI;
    private final DestinationGenerator destinationGenerator;
    private final CodeEmitter<T> emitter;
    private final OperationFilter filter;
    private final String path;
    private final String dtoPackageName;

    OperationVisitor(final OpenAPI openAPI, final CodeEmitter<T> emitter, final OperationFilter filter, final String path,
                     final DestinationGenerator destinationGenerator, final String dtoPackageName) {
        this.openAPI = openAPI;
        this.emitter = emitter;
        this.filter = filter;
        this.path = path;
        this.destinationGenerator = destinationGenerator;
        this.dtoPackageName = dtoPackageName;
    }

    List<String> asStringList(final List<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> stringList = new ArrayList<>();
        values.forEach(v -> {
            String s = String.valueOf(v);
            s = StringHelper.removeLeadingAndEndingQuotes(s);
            stringList.add(s);
        });

        return stringList;
    }

    CodeEmitter<T> emit(Parameter parameter) {
        // skip invalid parameters (such as when openapi spec refers to external schemas)
        String ref = parameter.get$ref();
        if (parameter.getName() == null && ref == null) {
            return emitter;
        }

        // parser may not resolve parameters (bug) so we try to lookup parameter via ref
        if (openAPI != null && parameter.getName() == null && ref.startsWith("#/components/parameters/")) {
            ref = ref.substring(24);
            var lookup = openAPI.getComponents().getParameters().get(ref);
            if (lookup != null) {
                parameter = lookup;
            }
        }

        if (parameter.getName() == null) {
            LOG.warn("OpenAPIV3Parser could not parse parameter (has no name): {}", parameter);
            return emitter;
        }

        emitter.emit("param");

        emit("name", parameter.getName());
        final String parameterType = parameter.getIn();
        if (ObjectHelper.isNotEmpty(parameterType)) {
            emit("type", RestParamType.valueOf(parameterType));
        }
        if (!"body".equals(parameterType)) {
            final Schema schema = parameter.getSchema();
            if (schema != null) {
                final String dataType = schema.getType();
                if (ObjectHelper.isNotEmpty(dataType)) {
                    emit("dataType", dataType);
                }
                emit("allowableValues", asStringList(schema.getEnum()));
                final StyleEnum style = parameter.getStyle();
                if (ObjectHelper.isNotEmpty(style)) {
                    if (style.equals(StyleEnum.FORM)) {
                        // Guard against null explode value
                        // See: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.3.md#fixed-fields-10
                        if (Boolean.FALSE.equals(parameter.getExplode())) {
                            emit("collectionFormat", CollectionFormat.csv);
                        } else {
                            emit("collectionFormat", CollectionFormat.multi);
                        }
                    }
                }
                if (ObjectHelper.isNotEmpty(schema.getDefault())) {
                    final String value = StringHelper.removeLeadingAndEndingQuotes(schema.getDefault().toString());
                    emit("defaultValue", value);
                }

                if ("array".equals(dataType) && schema.getItems() != null) {
                    emit("arrayType", schema.getItems().getType());
                }
            }
        }
        if (parameter.getRequired() != null) {
            emit("required", parameter.getRequired());
        } else {
            emit("required", Boolean.FALSE);
        }
        emit("description", parameter.getDescription());
        emitter.emit("endParam");

        return emitter;
    }

    CodeEmitter<T> emit(final String method, final Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return emitter;
        }

        return emitter.emit(method, new Object[] { values.toArray(new String[0]) });
    }

    CodeEmitter<T> emit(final String method, final Object value) {
        if (ObjectHelper.isEmpty(value)) {
            return emitter;
        }

        return emitter.emit(method, value);
    }

    void visit(final PathItem.HttpMethod method, final Operation operation, final PathItem pathItem) {
        if (filter.accept(operation.getOperationId())) {
            final String methodName = method.name().toLowerCase();
            emitter.emit(methodName, path);

            emit("id", operation.getOperationId());
            emit("description", operation.getDescription());
            Set<String> operationLevelConsumes = new LinkedHashSet<>();
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                operationLevelConsumes.addAll(operation.getRequestBody().getContent().keySet());
            }
            emit("consumes", operationLevelConsumes);
            Set<String> operationLevelProduces = new LinkedHashSet<>();
            if (operation.getResponses() != null) {
                for (ApiResponse response : operation.getResponses().values()) {
                    if (response.getContent() != null) {
                        operationLevelProduces.addAll(response.getContent().keySet());
                    }
                }
                ApiResponse response = operation.getResponses().get(ApiResponses.DEFAULT);
                if (response != null && response.getContent() != null) {
                    operationLevelProduces.addAll(response.getContent().keySet());
                }
            }
            emit("produces", operationLevelProduces);

            if (ObjectHelper.isNotEmpty(operation.getParameters())) {
                operation.getParameters().forEach(this::emit);
            }
            if (ObjectHelper.isNotEmpty(pathItem.getParameters())) {
                pathItem.getParameters().forEach(this::emit);
            }
            emitOperation(operation);

            emitter.emit("to", destinationGenerator.generateDestinationFor(operation));
        }
    }

    private CodeEmitter<T> emitOperation(final Operation operation) {
        if (operation.getRequestBody() != null) {
            String dto = null;
            boolean foundForm = false;
            final RequestBody requestBody = operation.getRequestBody();
            for (final Entry<String, MediaType> entry : requestBody.getContent().entrySet()) {
                final String ct = entry.getKey();
                MediaType mt = entry.getValue();
                if (ct.contains("form") && mt.getSchema().getProperties() != null) {
                    final Set<Map.Entry<String, Schema>> entrySet = mt.getSchema().getProperties().entrySet();
                    for (Map.Entry<String, Schema> entrySchema : entrySet) {
                        Schema openApi31Schema = entrySchema.getValue();
                        foundForm = true;
                        emitter.emit("param");
                        emit("name", entrySchema.getKey());
                        emit("type", RestParamType.formData);
                        emit("dataType", openApi31Schema.getType());
                        emit("required", requestBody.getRequired());
                        emit("description", entrySchema.getValue().getDescription());
                        emitter.emit("endParam");
                    }
                }
                if (dto == null) {
                    Schema schema = mt.getSchema();
                    boolean isArray = "array".equals(schema.getType());
                    String ref = isArray ? schema.getItems().get$ref() : schema.get$ref();
                    if (ref != null && ref.startsWith("#/components/schemas/")) {
                        dto = ref.substring(21);
                        if (isArray) {
                            dto += "[]";
                        }
                    }
                }
            }
            if (!foundForm) {
                emitter.emit("param");
                emit("name", "body");
                emit("type", RestParamType.valueOf("body"));
                emit("required", Boolean.TRUE);
                emit("description", requestBody.getDescription());
                emitter.emit("endParam");
            }
            if (dtoPackageName != null && dto != null) {
                emit("type", dtoPackageName + "." + dto);
            }
        }

        if (operation.getResponses() != null) {
            String dto = null;
            for (String key : operation.getResponses().keySet()) {
                if ("200".equals(key)) {
                    ApiResponse response = operation.getResponses().get(key);
                    for (final Entry<String, MediaType> entry : response.getContent().entrySet()) {
                        final MediaType mediaType = entry.getValue();
                        if (dto == null) {
                            Schema schema = mediaType.getSchema();
                            boolean isArray = "array".equals(schema.getType());
                            String ref = isArray ? schema.getItems().get$ref() : schema.get$ref();
                            if (ref != null && ref.startsWith("#/components/schemas/")) {
                                dto = ref.substring(21);
                                if (isArray) {
                                    dto += "[]";
                                }
                            }
                        }
                    }
                }
            }
            if (dtoPackageName != null && dto != null) {
                emit("outType", dtoPackageName + "." + dto);
            }
        }

        return emitter;
    }

}
