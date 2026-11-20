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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.MimeType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.StringHelper;

/**
 * Data type uses Jackson Avro data format to marshal given JsonNode in Exchange body to a binary (byte array)
 * representation. Uses given Avro schema from the Exchange properties when marshalling the payload (usually already
 * resolved via schema resolver).
 */
@DataTypeTransformer(name = "avro-binary",
                     description = "Transforms from JSon to binary (byte array) using Jackson Avro (supports Avro schema)")
@BodyTypes(transformerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO },
                                     description = "Needs the Avro schema in the X-Content-Schema exchange property: a"
                                                   + " Json object or array as text, Avro binary, a JsonNode or a POJO; a"
                                                   + " Map or a null body fail. Bytes or a stream with the Content-Type"
                                                   + " application/avro or avro/binary are Avro binary already, and are kept as"
                                                   + " they are, without the schema"),
           transformerOut = @BodyType(value = { Carrier.BYTES, Carrier.STREAM }, javaType = "byte[]", format = "binary",
                                      description = "Avro binary as bytes; a body that is Avro binary already is kept,"
                                                    + " also a stream, with its Content-Type"))
public class AvroBinaryDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        if (isAvroBinary(message)) {
            // Avro binary already, as a marshal to Avro leaves it: kept as it is, with its Content-Type
            return;
        }

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

    /**
     * Whether the body is Avro binary already: bytes or a stream with the Content-Type {@code application/avro} or
     * {@code avro/binary}, as a marshal to Avro and this data type set it.
     */
    private static boolean isAvroBinary(Message message) {
        Object body = message.getBody();
        String contentType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
        if (!(body instanceof byte[] || body instanceof InputStream) || contentType == null) {
            return false;
        }
        String mediaType = StringHelper.before(contentType, ";", contentType).trim();
        return MimeType.AVRO.type().equalsIgnoreCase(mediaType) || MimeType.AVRO_BINARY.type().equalsIgnoreCase(mediaType);
    }

    private InputStream getBodyAsStream(Message message) throws InvalidPayloadException {
        InputStream bodyStream = message.getBody(InputStream.class);

        if (bodyStream == null) {
            bodyStream = new ByteArrayInputStream(message.getMandatoryBody(byte[].class));
        }

        return bodyStream;
    }
}
