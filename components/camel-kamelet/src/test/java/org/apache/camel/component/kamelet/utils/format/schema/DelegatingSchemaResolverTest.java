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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jackson3.SchemaHelper;
import org.apache.camel.component.jackson3.avro.transform.AvroSchemaResolver;
import org.apache.camel.component.jackson3.protobuf.transform.ProtobufSchemaResolver;
import org.apache.camel.component.jackson3.transform.JsonSchemaResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.avro.AvroSchema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatingSchemaResolverTest {

    private static final String AVRO_SCHEMA
            = "{\"type\":\"record\",\"name\":\"User\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

    @Test
    void usesJackson3WhenOnTheClasspath() {
        assertThat(resolverFor(new DelegatingSchemaResolver(), "avro/binary"))
                .isInstanceOf(AvroSchemaResolver.class);
        assertThat(resolverFor(new DelegatingSchemaResolver(), "protobuf/binary"))
                .isInstanceOf(ProtobufSchemaResolver.class);
        assertThat(resolverFor(new DelegatingSchemaResolver(), "application/json"))
                .isInstanceOf(JsonSchemaResolver.class);
    }

    @Test
    void fallsBackToJackson2WithoutJackson3() {
        DelegatingSchemaResolver delegating
                = new DelegatingSchemaResolver(List.of(new MissingJackson3(), new Jackson2SchemaResolvers()));

        assertThat(resolverFor(delegating, "avro/binary"))
                .isInstanceOf(org.apache.camel.component.jackson.avro.transform.AvroSchemaResolver.class);
        assertThat(resolverFor(delegating, "protobuf/binary"))
                .isInstanceOf(org.apache.camel.component.jackson.protobuf.transform.ProtobufSchemaResolver.class);
        assertThat(resolverFor(delegating, "application/json"))
                .isInstanceOf(org.apache.camel.component.jackson.transform.JsonSchemaResolver.class);
    }

    @Test
    void usesTargetMimeTypeForJavaObject() {
        DelegatingSchemaResolver delegating = new DelegatingSchemaResolver();
        delegating.setMimeType("application/x-java-object");

        assertThat(delegating.resolver()).isNull();

        delegating.setTargetMimeType("avro/binary");
        assertThat(delegating.resolver())
                .isInstanceOf(AvroSchemaResolver.class);
    }

    @Test
    void setsTheSchemaOfTheSameJacksonLine() throws Exception {
        CamelContext context = new DefaultCamelContext();

        Exchange jackson3 = processAvro(new DelegatingSchemaResolver(), context);
        assertThat(jackson3.getProperty(SchemaHelper.CONTENT_SCHEMA))
                .isInstanceOf(AvroSchema.class);

        Exchange jackson2 = processAvro(
                new DelegatingSchemaResolver(List.of(new MissingJackson3(), new Jackson2SchemaResolvers())), context);
        assertThat(jackson2.getProperty(SchemaHelper.CONTENT_SCHEMA))
                .isInstanceOf(com.fasterxml.jackson.dataformat.avro.AvroSchema.class);
    }

    @Test
    void failsWhenNoLineIsOnTheClasspath() {
        DelegatingSchemaResolver delegating
                = new DelegatingSchemaResolver(List.of(new MissingJackson3(), new MissingJackson2()));
        delegating.setMimeType("avro/binary");

        assertThatThrownBy(delegating::resolver)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Cannot create schema resolver for avro: add camel-jackson3-avro or camel-jackson-avro to the classpath");
    }

    private static Processor resolverFor(DelegatingSchemaResolver delegating, String mimeType) {
        delegating.setMimeType(mimeType);
        return delegating.resolver();
    }

    private static Exchange processAvro(DelegatingSchemaResolver delegating, CamelContext context) throws Exception {
        delegating.setCamelContext(context);
        delegating.setMimeType("avro/binary");
        delegating.setSchema(AVRO_SCHEMA);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("{\"name\":\"camel\"}");
        delegating.process(exchange);
        return exchange;
    }

    /**
     * Jackson 3 as if its artifacts were not on the classpath.
     */
    private static final class MissingJackson3 extends Jackson3SchemaResolvers {
        @Override
        Processor doCreate(Format format, String schema, String contentClass) {
            throw new NoClassDefFoundError(className(format));
        }

        @Override
        String className(Format format) {
            return "missing." + super.className(format);
        }
    }

    /**
     * Jackson 2 as if its artifacts were not on the classpath.
     */
    private static final class MissingJackson2 extends Jackson2SchemaResolvers {
        @Override
        Processor doCreate(Format format, String schema, String contentClass) {
            throw new NoClassDefFoundError(className(format));
        }

        @Override
        String className(Format format) {
            return "missing." + super.className(format);
        }
    }
}
