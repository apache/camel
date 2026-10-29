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

package org.apache.camel.component.jackson3.avro.transform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.avro.NameValidator;
import org.apache.avro.Schema;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson3.SchemaHelper;
import org.apache.camel.component.jackson3.transform.Json;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.avro.AvroSchema;

class AvroBinaryDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final AvroBinaryDataTypeTransformer transformer = new AvroBinaryDataTypeTransformer();

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody("""
                    { "name": "Christoph", "age": 32 }
                """);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    { "name": "Christoph", "age": 32 }
                """, Json.mapper().writeValueAsString(
                Avro.mapper().reader().with(avroSchema).readTree(exchange.getMessage().getBody(byte[].class))), true);
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = new AvroSchema(
                new Schema.Parser(NameValidator.UTF_VALIDATOR)
                        .parse(AvroBinaryDataTypeTransformerTest.class.getResourceAsStream("Person.avsc")));
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    {"name":"Mickey","age":20}
                """, Json.mapper().writeValueAsString(
                Avro.mapper().reader().with(avroSchema).readTree(exchange.getMessage().getBody(byte[].class))), true);
    }

    @Test
    void shouldHandleJsonNode() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody(Json.mapper().readerFor(JsonNode.class).readValue("""
                    { "name": "Goofy", "age": 25 }
                """));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    {"name":"Goofy","age":25}
                """, Json.mapper().writeValueAsString(
                Avro.mapper().reader().with(avroSchema).readTree(exchange.getMessage().getBody(byte[].class))), true);
    }

    @Test
    void shouldHandleExplicitContentClass() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(new Person("Donald", 19));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    {"name":"Donald","age":19}
                """, Json.mapper().writeValueAsString(
                Avro.mapper().reader().with(avroSchema).readTree(exchange.getMessage().getBody(byte[].class))), true);
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("avro-binary"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(AvroBinaryDataTypeTransformer.class, transformer.getClass());
    }

    private AvroSchema getSchema() throws IOException {
        return new AvroSchema(
                new Schema.Parser(NameValidator.UTF_VALIDATOR)
                        .parse(AvroBinaryDataTypeTransformerTest.class.getResourceAsStream("Person.avsc")));
    }

    @Test
    void theBodyTypesSayTheBody(@TempDir Path dir) throws Exception {
        // CAMEL-25928: Json text, Avro binary as bytes, a stream or a file, a POJO or a JsonNode, as transformerIn
        // says, and the Avro binary bytes transformerOut says
        BodyTypes bodyTypes = AvroBinaryDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        Assertions.assertEquals(List.of(Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO),
                List.of(bodyTypes.transformerIn().value()));
        Assertions.assertEquals(List.of(Carrier.BYTES), List.of(bodyTypes.transformerOut().value()));
        Assertions.assertEquals(byte[].class.getCanonicalName(), bodyTypes.transformerOut().javaType());
        Assertions.assertEquals("binary", bodyTypes.transformerOut().format());
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] binary = Avro.mapper().writer(getSchema()).writeValueAsBytes(new Person("Goofy", 25));
        Path file = Files.write(dir.resolve("person.bin"), binary);
        for (Object body : Arrays.asList(json, binary, new ByteArrayInputStream(binary), file.toFile(), new Person("Goofy", 25),
                Json.mapper().readerFor(JsonNode.class).readValue(json))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            Assertions.assertInstanceOf(byte[].class, exchange.getMessage().getBody(), String.valueOf(body));
        }
        // a Map is no Json
        Exchange other = new DefaultExchange(camelContext);
        other.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
        other.getMessage().setBody(Map.of("name", "Goofy"));
        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
    }
}
