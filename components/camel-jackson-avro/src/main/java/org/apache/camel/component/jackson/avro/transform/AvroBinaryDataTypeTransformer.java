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

package org.apache.camel.component.jackson.avro.transform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
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
 * Data type uses Jackson Avro data format to marshal given JsonNode in Exchange body to a binary (byte array)
 * representation. Uses given Avro schema from the Exchange properties when marshalling the payload (usually already
 * resolved via schema resolver).
 */
@DataTypeTransformer(name = "avro-binary",
                     description = "Transforms from JSon to binary (byte array) using Jackson Avro (supports Avro schema)")
public class AvroBinaryDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        AvroSchema schema = message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA, AvroSchema.class);

        if (schema == null) {
            throw new CamelExecutionException("Missing proper avro schema for data type processing", message.getExchange());
        }

        try {
            byte[] marshalled;

            String contentClass = SchemaHelper.resolveContentClass(message.getExchange(), null);
            if (contentClass != null) {
                Class<?> contentType
                        = message.getExchange().getContext().getClassResolver().resolveMandatoryClass(contentClass);
                marshalled = Avro.mapper().writer().forType(contentType).with(schema)
                        .writeValueAsBytes(message.getBody());
            } else {
                marshalled = Avro.mapper().writer().forType(JsonNode.class).with(schema)
                        .writeValueAsBytes(getBodyAsJsonNode(message, schema));
            }

            message.setBody(marshalled);

            message.setHeader(Exchange.CONTENT_TYPE, MimeType.AVRO_BINARY.type());
            message.setHeader(SchemaHelper.CONTENT_SCHEMA, schema.getAvroSchema().getFullName());
        } catch (InvalidPayloadException | IOException | ClassNotFoundException e) {
            throw new CamelExecutionException("Failed to apply Avro binary data type on exchange", message.getExchange(), e);
        }
    }

    private JsonNode getBodyAsJsonNode(Message message, AvroSchema schema)
            throws InvalidPayloadException, IOException, ClassNotFoundException {
        if (message.getBody() instanceof JsonNode) {
            return (JsonNode) message.getBody();
        }

        if (message.getBody() instanceof String jsonString && Json.isJson(jsonString)) {
            return Json.mapper().readerFor(JsonNode.class).readTree(jsonString);
        }

        return Avro.mapper().reader().forType(JsonNode.class).with(schema)
                .readValue(getBodyAsStream(message));
    }

    private InputStream getBodyAsStream(Message message) throws InvalidPayloadException {
        InputStream bodyStream = message.getBody(InputStream.class);

        if (bodyStream == null) {
            bodyStream = new ByteArrayInputStream(message.getMandatoryBody(byte[].class));
        }

        return bodyStream;
    }
}
