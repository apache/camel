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
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.SchemaResolver;
import org.apache.camel.component.jackson.SchemaType;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Schema resolver able to read Protobuf schema from given exchange being processed. First attempt is to retrieve the
 * Protobuf schema reference from exchange properties. Second approach is to convert a given Json schema to a proper
 * Protobuf schema also reading from the exchange properties. Last approach it to load the Protobuf schema as a
 * classpath resource. The following resource path is used to resolve the schema {@code classpath:schemas/asvc/<T>.asvc}
 * where T represents the content class name of the exchange body. The content class information is either set as an
 * explicit exchange property or it gets resolved from exchange body type information.
 */
public class ProtobufSchemaResolver implements SchemaResolver, Processor {
    private final ConcurrentMap<String, ProtobufSchema> schemes;

    private ProtobufSchema schema;
    private String contentClass;

    public ProtobufSchemaResolver() {
        this.schemes = new ConcurrentHashMap<>();
    }

    public String getSchema() {
        if (this.schema != null) {
            return this.schema.getSource().toString();
        }

        return null;
    }

    public void setSchema(String schema) {
        if (ObjectHelper.isNotEmpty(schema)) {
            try {
                this.schema = ProtobufSchemaLoader.std.parse(schema);
            } catch (IOException e) {
                throw new RuntimeCamelException("Failed tp parse Protobuf schema", e);
            }
        } else {
            this.schema = null;
        }
    }

    public String getContentClass() {
        return contentClass;
    }

    public void setContentClass(String contentClass) {
        if (ObjectHelper.isNotEmpty(contentClass)) {
            this.contentClass = contentClass;
        } else {
            this.contentClass = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object payload = exchange.getMessage().getBody();
        if (payload == null) {
            return;
        }

        ProtobufSchema answer = computeIfAbsent(exchange);

        if (answer != null) {
            exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, answer);
            exchange.setProperty(SchemaHelper.CONTENT_SCHEMA_TYPE, SchemaType.PROTOBUF.type());
            exchange.setProperty(SchemaHelper.CONTENT_CLASS, SchemaHelper.resolveContentClass(exchange, this.contentClass));
        }
    }

    @Override
    public FormatSchema resolve(Exchange exchange) {
        ProtobufSchema answer = exchange.getProperty(SchemaHelper.CONTENT_SCHEMA, ProtobufSchema.class);
        if (answer == null) {
            answer = computeIfAbsent(exchange);
        }

        return answer;
    }

    private ProtobufSchema computeIfAbsent(Exchange exchange) {
        if (this.schema != null) {
            return this.schema;
        }

        ProtobufSchema answer = exchange.getProperty(SchemaHelper.CONTENT_SCHEMA, ProtobufSchema.class);

        if (answer == null && exchange.getProperties().containsKey(SchemaHelper.SCHEMA)) {
            String schemaJson = exchange.getProperty(SchemaHelper.SCHEMA, String.class);
            try {
                answer = ProtobufSchemaLoader.std.parse(schemaJson);
            } catch (IOException e) {
                throw new RuntimeException("Unable to parse Protobuf schema", e);
            }
        }

        if (answer == null) {
            String contentClass = SchemaHelper.resolveContentClass(exchange, this.contentClass);
            if (contentClass != null) {
                answer = this.schemes.computeIfAbsent(contentClass, t -> {
                    Resource res = PluginHelper.getResourceLoader(exchange.getContext())
                            .resolveResource("classpath:schemas/" + SchemaType.PROTOBUF.name().toLowerCase(Locale.US) +
                                             "/" + t + "." + SchemaType.PROTOBUF.type());

                    try {
                        if (res.exists()) {
                            try (InputStream is = res.getInputStream()) {
                                if (is != null) {
                                    return Protobuf.mapper().schemaLoader().load(is);
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Unable to load Protobuf schema for type: " + t + ", resource: " + res.getLocation(), e);
                    }

                    try {
                        return Protobuf.mapper().generateSchemaFor(Class.forName(contentClass));
                    } catch (JsonMappingException | ClassNotFoundException e) {
                        throw new RuntimeException(
                                "Unable to compute Protobuf schema for type: " + t, e);
                    }
                });
            }
        }

        if (answer != null) {
            this.schema = answer;
        }

        return answer;
    }
}
