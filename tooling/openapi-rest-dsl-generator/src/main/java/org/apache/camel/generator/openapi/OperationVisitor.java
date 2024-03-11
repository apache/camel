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
import java.util.Map.Entry;
import java.util.Set;

import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.Schema;
import io.apicurio.datamodels.models.openapi.OpenApiMediaType;
import io.apicurio.datamodels.models.openapi.OpenApiOperation;
import io.apicurio.datamodels.models.openapi.OpenApiParameter;
import io.apicurio.datamodels.models.openapi.OpenApiPathItem;
import io.apicurio.datamodels.models.openapi.OpenApiResponse;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Items;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Operation;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Parameter;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30MediaType;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Operation;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Parameter;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30RequestBody;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Response;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Schema;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31MediaType;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31Operation;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31Parameter;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31RequestBody;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31Response;
import io.apicurio.datamodels.models.openapi.v31.OpenApi31Schema;
import io.apicurio.datamodels.refs.ReferenceUtil;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

class OperationVisitor<T> {

    private final DestinationGenerator destinationGenerator;
    private final CodeEmitter<T> emitter;
    private final OperationFilter filter;
    private final String path;
    private final String dtoPackageName;

    OperationVisitor(final CodeEmitter<T> emitter, final OperationFilter filter, final String path,
                     final DestinationGenerator destinationGenerator, final String dtoPackageName) {
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

    CodeEmitter<T> emit(final OpenApiParameter parameter) {
        emitter.emit("param");

        OpenApiParameter toUse = parameter;
        if (toUse instanceof OpenApi20Parameter) {
            String ref = ((OpenApi20Parameter) toUse).get$ref();
            if (ObjectHelper.isNotEmpty(ref)) {
                toUse = (OpenApi20Parameter) ReferenceUtil.resolveRef(ref, parameter);
            }
        } else if (toUse instanceof OpenApi30Parameter) {
            String ref = ((OpenApi30Parameter) toUse).get$ref();
            if (ObjectHelper.isNotEmpty(ref)) {
                toUse = (OpenApi30Parameter) ReferenceUtil.resolveRef(ref, parameter);
            }
        } else if (toUse instanceof OpenApi31Parameter) {
            String ref = ((OpenApi31Parameter) toUse).get$ref();
            if (ObjectHelper.isNotEmpty(ref)) {
                toUse = (OpenApi31Parameter) ReferenceUtil.resolveRef(ref, parameter);
            }
        }

        emit("name", toUse.getName());
        final String parameterType = toUse.getIn();
        if (ObjectHelper.isNotEmpty(parameterType)) {
            emit("type", RestParamType.valueOf(parameterType));
        }
        if (!"body".equals(parameterType)) {
            if (toUse instanceof OpenApi20Parameter) {
                final OpenApi20Parameter serializableParameter = (OpenApi20Parameter) toUse;

                final String dataType = serializableParameter.getType();
                emit("dataType", dataType);
                emit("allowableValues", asStringList(serializableParameter.getEnum()));
                final String collectionFormat = serializableParameter.getCollectionFormat();
                if (ObjectHelper.isNotEmpty(collectionFormat)) {
                    emit("collectionFormat", CollectionFormat.valueOf(collectionFormat));
                }
                if (ObjectHelper.isNotEmpty(serializableParameter.getDefault())) {
                    final String value
                            = StringHelper.removeLeadingAndEndingQuotes(serializableParameter.getDefault().toString());
                    emit("defaultValue", value);
                }

                final OpenApi20Items items = serializableParameter.getItems();
                if ("array".equals(dataType) && items != null) {
                    emit("arrayType", items.getType());
                }
            } else if (toUse instanceof OpenApi30Parameter) {
                final OpenApi30Parameter serializableParameter = (OpenApi30Parameter) toUse;
                final OpenApi30Schema schema = (OpenApi30Schema) serializableParameter.getSchema();
                if (schema != null) {
                    final String dataType = schema.getType();
                    if (ObjectHelper.isNotEmpty(dataType)) {
                        emit("dataType", dataType);
                    }
                    emit("allowableValues", asStringList(schema.getEnum()));
                    final String style = serializableParameter.getStyle();
                    if (ObjectHelper.isNotEmpty(style)) {
                        if (style.equals("form")) {
                            // Guard against null explode value
                            // See: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.3.md#fixed-fields-10
                            if (Boolean.FALSE.equals(serializableParameter.isExplode())) {
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
            } else if (toUse instanceof OpenApi31Parameter) {
                final OpenApi31Parameter serializableParameter = (OpenApi31Parameter) toUse;
                final OpenApi31Schema schema = (OpenApi31Schema) serializableParameter.getSchema();
                if (schema != null) {
                    final String dataType
                            = schema.getType() != null && schema.getType().isString() ? schema.getType().asString() : null;
                    if (ObjectHelper.isNotEmpty(dataType)) {
                        emit("dataType", dataType);
                    }
                    emit("allowableValues", asStringList(schema.getEnum()));
                    final String style = serializableParameter.getStyle();
                    if (ObjectHelper.isNotEmpty(style)) {
                        if (style.equals("form")) {
                            // Guard against null explode value
                            // See: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.3.md#fixed-fields-10
                            if (Boolean.FALSE.equals(serializableParameter.isExplode())) {
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
                        emit("arrayType", schema.getItems().getType().asString());
                    }
                }
            }
        }
        if (toUse.isRequired() != null) {
            emit("required", toUse.isRequired());
        } else {
            emit("required", Boolean.FALSE);
        }
        emit("description", toUse.getDescription());
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

    void visit(final PathVisitor.HttpMethod method, final OpenApiOperation operation) {
        if (filter.accept(operation.getOperationId())) {
            final String methodName = method.name().toLowerCase();
            emitter.emit(methodName, path);

            emit("id", operation.getOperationId());
            emit("description", operation.getDescription());
            Set<String> operationLevelConsumes = new LinkedHashSet<>();
            if (operation instanceof OpenApi20Operation) {
                OpenApi20Operation oas20Operation = (OpenApi20Operation) operation;
                if (oas20Operation.getConsumes() != null) {
                    operationLevelConsumes.addAll(oas20Operation.getConsumes());
                }
            } else if (operation instanceof OpenApi30Operation) {
                OpenApi30Operation oas30Operation = (OpenApi30Operation) operation;
                if (oas30Operation.getRequestBody() != null
                        && oas30Operation.getRequestBody().getContent() != null) {
                    operationLevelConsumes.addAll(oas30Operation.getRequestBody().getContent().keySet());
                }
            } else if (operation instanceof OpenApi31Operation) {
                OpenApi31Operation oas31Operation = (OpenApi31Operation) operation;
                if (oas31Operation.getRequestBody() != null
                        && oas31Operation.getRequestBody().getContent() != null) {
                    operationLevelConsumes.addAll(oas31Operation.getRequestBody().getContent().keySet());
                }
            }
            emit("consumes", operationLevelConsumes);
            Set<String> operationLevelProduces = new LinkedHashSet<>();
            if (operation instanceof OpenApi20Operation) {
                OpenApi20Operation oas20Operation = (OpenApi20Operation) operation;
                if (oas20Operation.getProduces() != null) {
                    operationLevelProduces.addAll(oas20Operation.getProduces());
                }
            } else if (operation instanceof OpenApi30Operation) {
                final OpenApi30Operation oas30Operation = (OpenApi30Operation) operation;
                if (oas30Operation.getResponses() != null) {
                    for (OpenApiResponse response : oas30Operation.getResponses().getItems()) {
                        OpenApi30Response oas30Response = (OpenApi30Response) response;
                        if (oas30Response.getContent() != null) {
                            operationLevelProduces.addAll(oas30Response.getContent().keySet());
                        }
                    }
                    OpenApi30Response oas30Response = (OpenApi30Response) oas30Operation.getResponses().getDefault();
                    if (oas30Response != null && oas30Response.getContent() != null) {
                        operationLevelProduces.addAll(oas30Response.getContent().keySet());
                    }
                }
            } else if (operation instanceof OpenApi31Operation) {
                final OpenApi31Operation oas31Operation = (OpenApi31Operation) operation;
                if (oas31Operation.getResponses() != null) {
                    for (OpenApiResponse response : oas31Operation.getResponses().getItems()) {
                        OpenApi31Response oas31Response = (OpenApi31Response) response;
                        if (oas31Response.getContent() != null) {
                            operationLevelProduces.addAll(oas31Response.getContent().keySet());
                        }
                    }
                    OpenApi31Response oas31Response = (OpenApi31Response) oas31Operation.getResponses().getDefault();
                    if (oas31Response != null && oas31Response.getContent() != null) {
                        operationLevelProduces.addAll(oas31Response.getContent().keySet());
                    }
                }
            }
            emit("produces", operationLevelProduces);

            if (ObjectHelper.isNotEmpty(operation.getParameters())) {
                operation.getParameters().forEach(this::emit);
            }
            final OpenApiPathItem pathItem = (OpenApiPathItem) operation.parent();
            if (ObjectHelper.isNotEmpty(pathItem.getParameters())) {
                pathItem.getParameters().forEach(this::emit);
            }
            if (operation instanceof OpenApi30Operation) {
                emitOas30Operation((OpenApi30Operation) operation);
            } else if (operation instanceof OpenApi31Operation) {
                emitOas31Operation((OpenApi31Operation) operation);
            }

            emitter.emit("to", destinationGenerator.generateDestinationFor(operation));
        }
    }

    private CodeEmitter<T> emitOas30Operation(final OpenApi30Operation operation) {
        if (operation.getRequestBody() != null) {
            String dto = null;
            boolean foundForm = false;
            final OpenApi30RequestBody requestBody = operation.getRequestBody();
            for (final Entry<String, OpenApiMediaType> entry : requestBody.getContent().entrySet()) {
                final String ct = entry.getKey();
                OpenApi30MediaType mt = (OpenApi30MediaType) entry.getValue();
                if (ct.contains("form") && mt.getSchema().getProperties() != null) {
                    for (final Entry<String, Schema> entrySchema : mt.getSchema().getProperties().entrySet()) {
                        OpenApi30Schema openApi30Schema = (OpenApi30Schema) entrySchema.getValue();
                        foundForm = true;
                        emitter.emit("param");
                        emit("name", entrySchema.getKey());
                        emit("type", RestParamType.formData);
                        emit("dataType", openApi30Schema.getType());
                        emit("required", requestBody.isRequired());
                        emit("description", entrySchema.getValue().getDescription());
                        emitter.emit("endParam");
                    }
                }
                if (dto == null && mt.getSchema() instanceof Referenceable ref) {
                    OpenApi30Schema schema = (OpenApi30Schema) mt.getSchema();
                    boolean array = "array".equals(schema.getType());
                    if (array) {
                        ref = schema.getItems();
                    }
                    String r = ref.get$ref();
                    if (r != null && r.startsWith("#/components/schemas/")) {
                        dto = r.substring(21);
                        if (array) {
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
            for (String key : operation.getResponses().getItemNames()) {
                if ("200".equals(key)) {
                    var response = operation.getResponses().getItem(key);
                    if (response instanceof OpenApi30Response res30) {
                        for (final Entry<String, OpenApi30MediaType> entry : res30.getContent().entrySet()) {
                            final OpenApi30MediaType mediaType = entry.getValue();
                            if (dto == null && mediaType.getSchema() instanceof Referenceable ref) {
                                OpenApi30Schema schema = (OpenApi30Schema) mediaType.getSchema();
                                boolean array = "array".equals(schema.getType());
                                if (array) {
                                    ref = schema.getItems();
                                }
                                String r = ref.get$ref();
                                if (r != null && r.startsWith("#/components/schemas/")) {
                                    dto = r.substring(21);
                                    if (array) {
                                        dto += "[]";
                                    }
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

    private CodeEmitter<T> emitOas31Operation(final OpenApi31Operation operation) {
        if (operation.getRequestBody() != null) {
            String dto = null;
            boolean foundForm = false;
            final OpenApi31RequestBody requestBody = operation.getRequestBody();
            for (final Entry<String, OpenApiMediaType> entry : requestBody.getContent().entrySet()) {
                final String ct = entry.getKey();
                OpenApi31MediaType mt = (OpenApi31MediaType) entry.getValue();
                if (ct.contains("form") && mt.getSchema().getProperties() != null) {
                    for (final Entry<String, Schema> entrySchema : mt.getSchema().getProperties().entrySet()) {
                        OpenApi31Schema openApi31Schema = (OpenApi31Schema) entrySchema.getValue();
                        foundForm = true;
                        emitter.emit("param");
                        emit("name", entrySchema.getKey());
                        emit("type", RestParamType.formData);
                        emit("dataType", openApi31Schema.getType());
                        emit("required", requestBody.isRequired());
                        emit("description", entrySchema.getValue().getDescription());
                        emitter.emit("endParam");
                    }
                }
                if (dto == null && mt.getSchema() instanceof Referenceable ref) {
                    OpenApi31Schema schema = (OpenApi31Schema) mt.getSchema();
                    boolean array = "array".equals(
                            schema.getType() != null && schema.getType().isString() ? schema.getType().asString() : false);
                    if (array) {
                        ref = schema.getItems();
                    }
                    String r = ref.get$ref();
                    if (r != null && r.startsWith("#/components/schemas/")) {
                        dto = r.substring(21);
                        if (array) {
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
            for (String key : operation.getResponses().getItemNames()) {
                if ("200".equals(key)) {
                    var response = operation.getResponses().getItem(key);
                    if (response instanceof OpenApi31Response res31) {
                        for (final Entry<String, OpenApi31MediaType> entry : res31.getContent().entrySet()) {
                            final OpenApi31MediaType mediaType = entry.getValue();
                            if (dto == null && mediaType.getSchema() instanceof Referenceable ref) {
                                OpenApi31Schema schema = (OpenApi31Schema) mediaType.getSchema();
                                boolean array
                                        = "array".equals(schema.getType() != null && schema.getType().isString()
                                                ? schema.getType().asString() : false);
                                if (array) {
                                    ref = schema.getItems();
                                }
                                String r = ref.get$ref();
                                if (r != null && r.startsWith("#/components/schemas/")) {
                                    dto = r.substring(21);
                                    if (array) {
                                        dto += "[]";
                                    }
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
