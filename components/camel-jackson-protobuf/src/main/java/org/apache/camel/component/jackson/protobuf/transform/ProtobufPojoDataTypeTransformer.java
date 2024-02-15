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
package org.apache.camel.component.jackson.protobuf.transform;

import java.io.IOException;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.jackson.SchemaType;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.component.jackson.transform.JsonPojoDataTypeTransformer;
import org.apache.camel.spi.DataTypeTransformer;

/**
 * Data type able to unmarshal Exchange body to Java object. Supports both Protobuf schema types and uses Jackson
 * Protobuf object mapper implementation for the unmarshal operation. Requires proper setting of content schema, class
 * and schema type in Exchange properties (usually resolved via Json schema resolver).
 */
@DataTypeTransformer(name = "protobuf-x-java-object",
                     description = "Transforms from JSon to Java object using Jackson Protobuf (supports content schema)")
public class ProtobufPojoDataTypeTransformer extends JsonPojoDataTypeTransformer {

    @Override
    protected Object getJavaObject(Message message, SchemaType schemaType, FormatSchema schema, Class<?> contentType)
            throws InvalidPayloadException, IOException {
        if (message.getBody() instanceof String jsonString && Json.isJson(jsonString)) {
            return super.getJavaObject(message, SchemaType.JSON, schema, contentType);
        }

        if (schemaType == SchemaType.PROTOBUF) {
            if (schema == null) {
                throw new CamelExecutionException(
                        "Missing proper Protobuf schema for Java object data type processing", message.getExchange());
            }

            if (message.getBody() instanceof JsonNode) {
                return Protobuf.mapper().reader().forType(contentType).with(schema).readValue(message.getBody(JsonNode.class));
            }

            return Protobuf.mapper().reader().forType(contentType).with(schema).readValue(getBodyAsStream(message));
        }

        return super.getJavaObject(message, schemaType, schema, contentType);
    }
}
