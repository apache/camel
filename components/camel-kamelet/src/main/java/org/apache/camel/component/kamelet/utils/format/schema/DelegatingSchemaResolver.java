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

package org.apache.camel.component.kamelet.utils.format.schema;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jackson.avro.transform.AvroSchemaResolver;
import org.apache.camel.component.jackson.protobuf.transform.ProtobufSchemaResolver;
import org.apache.camel.component.jackson.transform.JsonSchemaResolver;
import org.apache.camel.component.kamelet.utils.format.MimeType;
import org.apache.camel.util.ObjectHelper;

/**
 * Schema resolver processor delegates to either Avro or Json schema resolver based on the given mimetype property. When
 * mimetype is of type application/x-java-object uses additional target mimetype (usually the produces mimetype) to
 * determine the schema resolver (Avro or Json). Delegates to schema resolver and sets proper content class and schema
 * properties on the delegate.
 */
public class DelegatingSchemaResolver implements Processor, CamelContextAware {

    private static final String AVRO_SCHEMA_RESOLVER = "org.apache.camel.component.jackson.avro.transform.AvroSchemaResolver";
    private static final String PROTOBUF_SCHEMA_RESOLVER
            = "org.apache.camel.component.jackson.protobuf.transform.ProtobufSchemaResolver";
    private static final String JSON_SCHEMA_RESOLVER = "org.apache.camel.component.jackson.transform.JsonSchemaResolver";

    private CamelContext camelContext;
    private String mimeType;
    private String targetMimeType;

    private String schema;
    private String contentClass;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(mimeType)) {
            return;
        }

        MimeType mimeType = MimeType.of(this.mimeType);
        Processor resolver;
        if (mimeType.equals(MimeType.JAVA_OBJECT)) {
            if (ObjectHelper.isEmpty(targetMimeType)) {
                return;
            }
            resolver = fromMimeType(MimeType.of(targetMimeType));
        } else {
            resolver = fromMimeType(mimeType);
        }

        if (resolver != null) {
            resolver.process(exchange);
        }
    }

    private Processor fromMimeType(MimeType mimeType) {
        switch (mimeType) {
            case PROTOBUF:
            case PROTOBUF_BINARY:
            case PROTOBUF_STRUCT:
                return createSchemaResolver(PROTOBUF_SCHEMA_RESOLVER);
            case AVRO:
            case AVRO_BINARY:
            case AVRO_STRUCT:
                return createSchemaResolver(AVRO_SCHEMA_RESOLVER);
            case JSON:
            case STRUCT:
                return createSchemaResolver(JSON_SCHEMA_RESOLVER);
            default:
                return null;
        }
    }

    private Processor createSchemaResolver(String className) {
        try {
            return doCreateSchemaResolver(className);
        } catch (NoClassDefFoundError e) {
            // fallback to dynamic class resolution (e.g. Camel CLI with downloaded dependencies)
            return doCreateSchemaResolverReflection(className);
        }
    }

    private Processor doCreateSchemaResolver(String className) {
        switch (className) {
            case PROTOBUF_SCHEMA_RESOLVER:
                ProtobufSchemaResolver protobufSchemaResolver = new ProtobufSchemaResolver();
                protobufSchemaResolver.setSchema(this.schema);
                protobufSchemaResolver.setContentClass(this.contentClass);
                return protobufSchemaResolver;
            case AVRO_SCHEMA_RESOLVER:
                AvroSchemaResolver avroSchemaResolver = new AvroSchemaResolver();
                avroSchemaResolver.setSchema(this.schema);
                avroSchemaResolver.setContentClass(this.contentClass);
                return avroSchemaResolver;
            case JSON_SCHEMA_RESOLVER:
                JsonSchemaResolver jsonSchemaResolver = new JsonSchemaResolver();
                jsonSchemaResolver.setSchema(this.schema);
                jsonSchemaResolver.setContentClass(this.contentClass);
                return jsonSchemaResolver;
            default:
                return null;
        }
    }

    private Processor doCreateSchemaResolverReflection(String className) {
        try {
            Class<?> clazz;
            if (camelContext != null) {
                clazz = camelContext.getClassResolver().resolveMandatoryClass(className);
            } else {
                clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            }
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getMethod("setSchema", String.class).invoke(instance, this.schema);
            clazz.getMethod("setContentClass", String.class).invoke(instance, this.contentClass);
            return (Processor) instance;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create schema resolver: " + className, e);
        }
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getContentClass() {
        return contentClass;
    }

    public void setContentClass(String contentClass) {
        this.contentClass = contentClass;
    }

    public String getTargetMimeType() {
        return targetMimeType;
    }

    public void setTargetMimeType(String targetMimeType) {
        this.targetMimeType = targetMimeType;
    }
}
