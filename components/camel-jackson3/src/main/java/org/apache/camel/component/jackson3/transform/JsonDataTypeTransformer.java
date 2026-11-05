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
package org.apache.camel.component.jackson3.transform;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.jackson3.SchemaHelper;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.MimeType;
import org.apache.camel.spi.Transformer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Data type uses Jackson data format to marshal given Exchange payload to a Json (binary byte array representation).
 * Json text is kept, other text becomes a Json string, and a Map, a List, a JsonNode or a POJO is written as Json.
 * Bytes and a stream are read as text with the charset of the exchange.
 */
@DataTypeTransformer(name = "application-json",
                     description = "Transforms from JSon to binary (byte array) using Jackson 3")
@BodyTypes(transformerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.NULL },
                                     description = "A Json object or array as text, bytes or a stream, a Map, a List, a"
                                                   + " JsonNode or a POJO; other text, also a Json number, boolean, null or"
                                                   + " quoted string, becomes a Json string; bytes and a stream are read as"
                                                   + " text with the exchange charset, so a payload that is not text must be"
                                                   + " encoded first, such as in Base64, or given a content class; with the"
                                                   + " X-Content-Class exchange property only a Json object or array as text"
                                                   + " or a body of that class; a null body becomes null"),
           transformerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]", format = "json"))
public class JsonDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        try {
            byte[] marshalled;
            Object body = message.getBody();
            boolean hasContentClass = message.getExchange().getProperty(SchemaHelper.CONTENT_CLASS, String.class) != null;
            if (body instanceof String jsonString && Json.isJson(jsonString)) {
                marshalled = jsonString.getBytes(StandardCharsets.UTF_8);
            } else if (!hasContentClass && (body instanceof Map || body instanceof Collection)) {
                // a Map or a List has no class to write it as: it becomes a Json object or array
                marshalled = Json.mapper().writeValueAsBytes(body);
            } else if (!hasContentClass && isText(body)) {
                // read as text with the exchange charset: a Json object or array is kept, and any other text, also a
                // Json number, boolean, null or quoted string, becomes a Json string
                String text = message.getMandatoryBody(String.class);
                marshalled = Json.isJson(text) ? text.getBytes(StandardCharsets.UTF_8) : Json.mapper().writeValueAsBytes(text);
            } else {
                String contentClass = SchemaHelper.resolveContentClass(message.getExchange(), JsonNode.class.getName());
                Class<?> contentType
                        = message.getExchange().getContext().getClassResolver().resolveMandatoryClass(contentClass);

                marshalled = Json.mapper().writer().forType(contentType).writeValueAsBytes(body);
            }
            message.setBody(marshalled);

            message.setHeader(Exchange.CONTENT_TYPE, MimeType.JSON.type());

            String contentSchema = message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA, String.class);
            if (contentSchema != null) {
                message.setHeader(SchemaHelper.CONTENT_SCHEMA, contentSchema);
            }
        } catch (InvalidPayloadException | JacksonException | ClassNotFoundException e) {
            throw new CamelExecutionException("Failed to apply Json output data type on exchange", message.getExchange(), e);
        }
    }

    /**
     * Text, bytes or a stream, which hold Json as text.
     */
    private static boolean isText(Object body) {
        return body instanceof String || body instanceof byte[] || body instanceof InputStream || body instanceof Reader;
    }
}
