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
package org.apache.camel.generator.swagger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;

import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.util.ObjectHelper;

class OperationVisitor<T> {

    private final DestinationGenerator destinationGenerator;

    private final CodeEmitter<T> emitter;

    private final String path;

    OperationVisitor(final CodeEmitter<T> emitter, final String path, final DestinationGenerator destinationGenerator) {
        this.emitter = emitter;
        this.path = path;
        this.destinationGenerator = destinationGenerator;
    }

    List<String> asStringList(final List<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> stringList = new ArrayList<>();
        values.forEach(v -> stringList.add(String.valueOf(v)));

        return stringList;
    }

    CodeEmitter<T> emit(final Parameter parameter) {
        emitter.emit("param");
        emit("name", parameter.getName());
        final String parameterType = parameter.getIn();
        if (ObjectHelper.isNotEmpty(parameterType)) {
            emit("type", RestParamType.valueOf(parameterType));
        }
        if (parameter instanceof AbstractSerializableParameter) {
            final AbstractSerializableParameter serializableParameter = (AbstractSerializableParameter) parameter;

            final String dataType = serializableParameter.getType();
            emit("dataType", dataType);
            emit("allowableValues", asStringList(serializableParameter.getEnumValue()));
            final String collectionFormat = serializableParameter.getCollectionFormat();
            if (ObjectHelper.isNotEmpty(collectionFormat)) {
                emit("collectionFormat", CollectionFormat.valueOf(collectionFormat));
            }
            emit("defaultValue", serializableParameter.getDefault());

            final Property items = serializableParameter.getItems();
            if ("array".equals(dataType) && items != null) {
                emit("arrayType", items.getType());
            }
        }
        emit("required", parameter.getRequired());
        emit("description", parameter.getDescription());
        emitter.emit("endParam");

        return emitter;
    }

    CodeEmitter<T> emit(final String method, final List<String> values) {
        if (values == null || values.isEmpty()) {
            return emitter;
        }

        return emitter.emit(method, new Object[] {values.toArray(new String[values.size()])});
    }

    CodeEmitter<T> emit(final String method, final Object value) {
        if (ObjectHelper.isEmpty(value)) {
            return emitter;
        }

        return emitter.emit(method, value);
    }

    void visit(final HttpMethod method, final Operation operation) {
        final String methodName = method.name().toLowerCase();
        emitter.emit(methodName, path);

        emit("id", operation.getOperationId());
        emit("description", operation.getDescription());
        emit("consumes", operation.getConsumes());
        emit("produces", operation.getProduces());

        operation.getParameters().forEach(parameter -> {
            emit(parameter);
        });

        emitter.emit("to", destinationGenerator.generateDestinationFor(operation));
    }
}