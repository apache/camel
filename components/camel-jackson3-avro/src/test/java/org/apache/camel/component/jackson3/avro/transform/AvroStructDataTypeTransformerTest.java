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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;

class AvroStructDataTypeTransformerTest {
    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final AvroStructDataTypeTransformer transformer = new AvroStructDataTypeTransformer();

    @Test
    void shouldHandleAvroBinary() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody(AvroMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                .writer(avroSchema).writeValueAsBytes(new Person("Christoph", 32)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        Person person = Json.mapper().readValue("""
                    { "name": "Christoph", "age": 32 }
                """, Person.class);
        exchange.getMessage().setBody(AvroMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                .writer(avroSchema).writeValueAsBytes(person));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
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

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    void shouldHandleExplicitContentClass() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(new Person("Donald", 19));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("avro-x-struct"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(AvroStructDataTypeTransformer.class, transformer.getClass());
    }

    private AvroSchema getSchema() throws IOException {
        return new AvroSchema(
                new Schema.Parser(NameValidator.UTF_VALIDATOR)
                        .parse(AvroStructDataTypeTransformerTest.class.getResourceAsStream("Person.avsc")));
    }

    @Test
    void theBodyTypesSayTheBody(@TempDir Path dir) throws Exception {
        // CAMEL-25928: Avro binary as bytes, a stream or a file, a POJO or a JsonNode, as transformerIn says, and
        // the JsonNode transformerOut says
        BodyTypes bodyTypes = AvroStructDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        Assertions.assertEquals(List.of(Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO),
                List.of(bodyTypes.transformerIn().value()));
        Assertions.assertEquals(List.of(Carrier.POJO), List.of(bodyTypes.transformerOut().value()));
        Assertions.assertEquals(JsonNode.class.getName(), bodyTypes.transformerOut().javaType());
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] binary = Avro.mapper().writer(getSchema()).writeValueAsBytes(new Person("Goofy", 25));
        Path file = Files.write(dir.resolve("person.bin"), binary);
        for (Object body : Arrays.asList(binary, new ByteArrayInputStream(binary), file.toFile(), new Person("Goofy", 25),
                Json.mapper().readerFor(JsonNode.class).readValue(json))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            Assertions.assertInstanceOf(JsonNode.class, exchange.getMessage().getBody(), String.valueOf(body));
        }
        // a Map is no Avro binary
        Exchange other = new DefaultExchange(camelContext);
        other.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
        other.getMessage().setBody(Map.of("name", "Goofy"));
        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
        // nor is Json text
        Exchange text = new DefaultExchange(camelContext);
        text.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
        text.getMessage().setBody(json);
        Assertions.assertThrows(RuntimeException.class,
                () -> transformer.transform(text.getMessage(), DataType.ANY, DataType.ANY));
    }
}
