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

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kamelet.utils.format.MimeType;
import org.apache.camel.component.kamelet.utils.format.schema.SchemaResolverFactory.Format;
import org.apache.camel.util.ObjectHelper;

/**
 * Schema resolver processor delegates to either Avro or Json schema resolver based on the given mimetype property. When
 * mimetype is of type application/x-java-object uses additional target mimetype (usually the produces mimetype) to
 * determine the schema resolver (Avro or Json). Delegates to schema resolver and sets proper content class and schema
 * properties on the delegate.
 * <p>
 * The schema resolver of camel-jackson3 is used when it is on the classpath, else the one of camel-jackson, as the data
 * formats and data type transformers do, so the schema it sets is of the type they read.
 */
public class DelegatingSchemaResolver implements Processor, CamelContextAware {

    private static final List<SchemaResolverFactory> FACTORIES
            = List.of(new Jackson3SchemaResolvers(), new Jackson2SchemaResolvers());

    private final List<SchemaResolverFactory> factories;

    private CamelContext camelContext;
    private String mimeType;
    private String targetMimeType;

    private String schema;
    private String contentClass;

    public DelegatingSchemaResolver() {
        this(FACTORIES);
    }

    DelegatingSchemaResolver(List<SchemaResolverFactory> factories) {
        this.factories = factories;
    }

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
        Processor resolver = resolver();
        if (resolver != null) {
            resolver.process(exchange);
        }
    }

    /**
     * The schema resolver for the mime types, or null when they need none.
     */
    Processor resolver() {
        if (ObjectHelper.isEmpty(mimeType)) {
            return null;
        }

        MimeType mimeType = MimeType.of(this.mimeType);
        if (mimeType.equals(MimeType.JAVA_OBJECT)) {
            if (ObjectHelper.isEmpty(targetMimeType)) {
                return null;
            }
            mimeType = MimeType.of(targetMimeType);
        }

        Format format = formatOf(mimeType);
        return format != null ? createSchemaResolver(format) : null;
    }

    private static Format formatOf(MimeType mimeType) {
        switch (mimeType) {
            case PROTOBUF:
            case PROTOBUF_BINARY:
            case PROTOBUF_STRUCT:
                return Format.PROTOBUF;
            case AVRO:
            case AVRO_BINARY:
            case AVRO_STRUCT:
                return Format.AVRO;
            case JSON:
            case STRUCT:
                return Format.JSON;
            default:
                return null;
        }
    }

    private Processor createSchemaResolver(Format format) {
        for (SchemaResolverFactory factory : factories) {
            Processor resolver = factory.create(format, schema, contentClass);
            if (resolver != null) {
                return resolver;
            }
        }
        // fallback to dynamic class resolution (e.g. Camel CLI with downloaded dependencies)
        for (SchemaResolverFactory factory : factories) {
            Class<?> clazz = resolveClass(factory.className(format));
            if (clazz != null) {
                return createSchemaResolverReflection(clazz);
            }
        }
        throw new IllegalStateException(
                "Cannot create schema resolver for " + format.name().toLowerCase(Locale.ROOT) + ": add "
                                        + factories.stream().map(f -> f.artifactId(format))
                                                .collect(Collectors.joining(" or "))
                                        + " to the classpath");
    }

    private Class<?> resolveClass(String className) {
        if (camelContext != null) {
            return camelContext.getClassResolver().resolveClass(className);
        }
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return null;
        }
    }

    private Processor createSchemaResolverReflection(Class<?> clazz) {
        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getMethod("setSchema", String.class).invoke(instance, this.schema);
            clazz.getMethod("setContentClass", String.class).invoke(instance, this.contentClass);
            return (Processor) instance;
        } catch (Exception e) {
            throw new RuntimeException("Cannot create schema resolver: " + clazz.getName(), e);
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
