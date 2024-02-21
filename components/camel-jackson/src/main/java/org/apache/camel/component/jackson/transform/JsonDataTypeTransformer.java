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
package org.apache.camel.component.jackson.transform;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.MimeType;
import org.apache.camel.spi.Transformer;

/**
 * Data type uses Jackson data format to marshal given Exchange payload to a Json (binary byte array representation).
 * Requires Exchange payload as JsonNode representation.
 */
@DataTypeTransformer(name = "application-json",
                     description = "Transforms from JSon to binary (byte array) using Jackson")
public class JsonDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        try {
            byte[] marshalled;
            if (message.getBody() instanceof String jsonString && Json.isJson(jsonString)) {
                marshalled = jsonString.getBytes(StandardCharsets.UTF_8);
            } else {
                String contentClass = SchemaHelper.resolveContentClass(message.getExchange(), JsonNode.class.getName());
                Class<?> contentType
                        = message.getExchange().getContext().getClassResolver().resolveMandatoryClass(contentClass);

                marshalled = Json.mapper().writer().forType(contentType).writeValueAsBytes(message.getBody());
            }
            message.setBody(marshalled);

            message.setHeader(Exchange.CONTENT_TYPE, MimeType.JSON.type());

            String contentSchema = message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA, String.class);
            if (contentSchema != null) {
                message.setHeader(SchemaHelper.CONTENT_SCHEMA, contentSchema);
            }
        } catch (JsonProcessingException | ClassNotFoundException e) {
            throw new CamelExecutionException("Failed to apply Json output data type on exchange", message.getExchange(), e);
        }
    }
}
