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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import org.apache.avro.NameValidator;
import org.apache.avro.Schema;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvroPojoDataTypeTransformerTest {
    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final AvroPojoDataTypeTransformer transformer = new AvroPojoDataTypeTransformer();

    @BeforeEach
    void setup() {
        transformer.setCamelContext(camelContext);
    }

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody("""
                    { "name": "Christoph", "age": 32 }
                """);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(Person.class, exchange.getMessage().getBody().getClass());
        Assertions.assertEquals("Christoph", exchange.getMessage().getBody(Person.class).name());
        Assertions.assertEquals(32, exchange.getMessage().getBody(Person.class).age());
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(Person.class, exchange.getMessage().getBody().getClass());
        Assertions.assertEquals("Mickey", exchange.getMessage().getBody(Person.class).name());
        Assertions.assertEquals(20, exchange.getMessage().getBody(Person.class).age());
    }

    @Test
    void shouldHandleAvroBinary() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage()
                .setBody(Avro.mapper().writer(avroSchema).writeValueAsBytes(new Person("Goofy", 25)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(Person.class, exchange.getMessage().getBody().getClass());
        Assertions.assertEquals("Goofy", exchange.getMessage().getBody(Person.class).name());
        Assertions.assertEquals(25, exchange.getMessage().getBody(Person.class).age());
    }

    @Test
    void shouldHandleAvroJsonNode() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage()
                .setBody(Avro.mapper().writerFor(JsonNode.class).with(avroSchema)
                        .writeValueAsBytes(Json.mapper().readTree("""
                                    { "name": "Goofy", "age": 25 }
                                """)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(Person.class, exchange.getMessage().getBody().getClass());
        Assertions.assertEquals("Goofy", exchange.getMessage().getBody(Person.class).name());
        Assertions.assertEquals(25, exchange.getMessage().getBody(Person.class).age());
    }

    @Test
    void shouldHandleExplicitContentClass() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        AvroSchema avroSchema = getSchema();
        exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, avroSchema);
        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(new Person("Donald", 19));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(Person.class, exchange.getMessage().getBody().getClass());
        Assertions.assertEquals("Donald", exchange.getMessage().getBody(Person.class).name());
        Assertions.assertEquals(19, exchange.getMessage().getBody(Person.class).age());
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("avro-x-java-object"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(AvroPojoDataTypeTransformer.class, transformer.getClass());
    }

    private AvroSchema getSchema() throws IOException {
        return new AvroSchema(
                new Schema.Parser(NameValidator.UTF_VALIDATOR)
                        .parse(AvroPojoDataTypeTransformerTest.class.getResourceAsStream("Person.avsc")));
    }

    @Test
    void theBodyTypesSayTheBody(@TempDir Path dir) throws Exception {
        // CAMEL-25928: Json text, or Avro binary as bytes, a stream or a file, as transformerIn says, read into the
        // class of the X-Content-Class property, which transformerOut leaves unknown
        BodyTypes bodyTypes = AvroPojoDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        Assertions.assertEquals(List.of(Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO),
                List.of(bodyTypes.transformerIn().value()));
        Assertions.assertEquals(List.of(Carrier.UNKNOWN), List.of(bodyTypes.transformerOut().value()));
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] binary = Avro.mapper().writer(getSchema()).writeValueAsBytes(new Person("Goofy", 25));
        Path file = Files.write(dir.resolve("person.bin"), binary);
        for (Object body : Arrays.asList(json, binary, new ByteArrayInputStream(binary), file.toFile())) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
            exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            Assertions.assertEquals(new Person("Goofy", 25), exchange.getMessage().getBody(), body.getClass().getName());
        }
        // a POJO is kept without the X-Content-Class property
        Object body = new Person("Goofy", 25);
        Exchange kept = new DefaultExchange(camelContext);
        kept.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
        kept.getMessage().setBody(body);
        transformer.transform(kept.getMessage(), DataType.ANY, DataType.ANY);
        Assertions.assertSame(body, kept.getMessage().getBody());
        // a Map is no Json
        Exchange other = new DefaultExchange(camelContext);
        other.setProperty(SchemaHelper.CONTENT_SCHEMA, getSchema());
        other.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        other.getMessage().setBody(Map.of("name", "Goofy"));
        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
    }
}
