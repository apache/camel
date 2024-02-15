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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.MimeType;
import org.apache.camel.spi.Transformer;

/**
 * Data type uses Protobuf Jackson data format to unmarshal Exchange body to generic JsonNode. Uses given Protobuf
 * schema from the Exchange properties when unmarshalling the payload (usually already resolved via schema resolver).
 */
@DataTypeTransformer(name = "protobuf-x-struct",
                     description = "Transforms to generic JSonNode using Jackson Protobuf (supports content schema)")
public class ProtobufStructDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        ProtobufSchema schema = message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA, ProtobufSchema.class);

        if (schema == null) {
            throw new CamelExecutionException("Missing proper Protobuf schema for data type processing", message.getExchange());
        }

        try {
            Object unmarshalled;
            String contentClass = SchemaHelper.resolveContentClass(message.getExchange(), null);
            if (contentClass != null) {
                Class<?> contentType
                        = message.getExchange().getContext().getClassResolver().resolveMandatoryClass(contentClass);
                unmarshalled = Protobuf.mapper().reader().forType(JsonNode.class).with(schema)
                        .readValue(Protobuf.mapper().writerFor(contentType).with(schema).writeValueAsBytes(message.getBody()));
            } else if (message.getBody() instanceof String jsonString && Json.isJson(jsonString)) {
                unmarshalled = Json.mapper().readTree(getBodyAsStream(message));
            } else {
                unmarshalled
                        = Protobuf.mapper().reader().forType(JsonNode.class).with(schema).readValue(getBodyAsStream(message));
            }

            message.setBody(unmarshalled);

            message.setHeader(Exchange.CONTENT_TYPE, MimeType.STRUCT.type());
        } catch (InvalidPayloadException | IOException | ClassNotFoundException e) {
            throw new CamelExecutionException(
                    "Failed to apply Protobuf x-struct data type on exchange", message.getExchange(), e);
        }
    }

    private InputStream getBodyAsStream(Message message) throws InvalidPayloadException {
        InputStream bodyStream = message.getBody(InputStream.class);

        if (bodyStream == null) {
            bodyStream = new ByteArrayInputStream(message.getMandatoryBody(byte[].class));
        }

        return bodyStream;
    }
}
