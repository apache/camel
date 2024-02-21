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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.fasterxml.jackson.core.FormatSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.SchemaType;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.MimeType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ObjectHelper;

/**
 * Data type able to unmarshal Exchange body to Java object. Supports both Json schema types and uses Jackson object
 * mapper implementation for the unmarshal operation. Requires proper setting of content schema, class and schema type
 * in Exchange properties (usually resolved via Json schema resolver).
 */
@DataTypeTransformer(name = "application-x-java-object",
                     description = "Transforms from JSon to Java object using Jackson (supports content schema)")
public class JsonPojoDataTypeTransformer extends Transformer implements CamelContextAware {

    private CamelContext camelContext;

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        ObjectHelper.notNull(camelContext, "camelContext");

        FormatSchema schema = message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA, FormatSchema.class);
        String contentClass = SchemaHelper.resolveContentClass(message.getExchange(), null);
        if (contentClass == null) {
            throw new CamelExecutionException(
                    "Missing content class information for Java object data type processing",
                    message.getExchange());
        }

        SchemaType schemaType = SchemaType.of(message.getExchange().getProperty(SchemaHelper.CONTENT_SCHEMA_TYPE,
                Optional.ofNullable(schema).map(FormatSchema::getSchemaType).orElse(SchemaType.JSON.name()), String.class));

        try {
            message.setHeader(Exchange.CONTENT_TYPE, MimeType.JAVA_OBJECT.type());

            Class<?> contentType = camelContext.getClassResolver().resolveMandatoryClass(contentClass);
            if (contentType.isAssignableFrom(message.getBody().getClass())) {
                return;
            }

            message.setBody(getJavaObject(message, schemaType, schema, contentType));
        } catch (InvalidPayloadException | IOException | ClassNotFoundException e) {
            throw new CamelExecutionException("Failed to apply Java object data type on exchange", message.getExchange(), e);
        }
    }

    protected Object getJavaObject(Message message, SchemaType schemaType, FormatSchema schema, Class<?> contentType)
            throws InvalidPayloadException, IOException {
        if (schemaType == SchemaType.JSON) {
            return Json.mapper().reader().forType(contentType).readValue(getBodyAsStream(message));
        } else {
            throw new CamelExecutionException(String.format("Unsupported schema type '%s'", schemaType), message.getExchange());
        }
    }

    protected InputStream getBodyAsStream(Message message) throws InvalidPayloadException {
        InputStream bodyStream = message.getBody(InputStream.class);

        if (bodyStream == null) {
            bodyStream = new ByteArrayInputStream(message.getMandatoryBody(byte[].class));
        }

        return bodyStream;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
