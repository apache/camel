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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson3.SchemaHelper;
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
import tools.jackson.databind.node.ObjectNode;

class JsonStructDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final JsonStructDataTypeTransformer transformer = new JsonStructDataTypeTransformer();

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody("""
                    { "name": "Christoph", "age": 32 }
                """);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
        JSONAssert.assertEquals("""
                    { "name": "Christoph", "age": 32 }
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
        JSONAssert.assertEquals("""
                    {"name":"Mickey","age":20}
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldHandleJsonNode() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(Json.mapper().readerFor(JsonNode.class).readValue("""
                    { "name": "Goofy", "age": 25 }
                """));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
        JSONAssert.assertEquals("""
                    {"name":"Goofy","age":25}
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldHandleExplicitContentClass() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        exchange.getMessage().setBody(new Person("Donald", 19));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(ObjectNode.class, exchange.getMessage().getBody().getClass());
        JSONAssert.assertEquals("""
                    {"name":"Donald","age":19}
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldWrapMalformedJsonInCamelExecutionException() {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody("{ this is no json }");

        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY));
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("application-x-struct"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(JsonStructDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    void theBodyTypesSayTheBody(@TempDir Path dir) throws Exception {
        // CAMEL-25928: Json as text, bytes, a stream or a file, a POJO or a null body, as transformerIn says, and
        // the JsonNode transformerOut says
        BodyTypes bodyTypes = JsonStructDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        Assertions.assertEquals(List.of(Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO, Carrier.NULL),
                List.of(bodyTypes.transformerIn().value()));
        Assertions.assertEquals(List.of(Carrier.POJO), List.of(bodyTypes.transformerOut().value()));
        Assertions.assertEquals(JsonNode.class.getName(), bodyTypes.transformerOut().javaType());
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(dir.resolve("person.json"), bytes);
        for (Object body : Arrays.asList(json, bytes, new ByteArrayInputStream(bytes), file.toFile(), new Person("Goofy", 25),
                null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            Assertions.assertInstanceOf(JsonNode.class, exchange.getMessage().getBody(), String.valueOf(body));
        }
        // a JsonNode is kept
        JsonNode body = Json.mapper().readerFor(JsonNode.class).readValue(json);
        Exchange kept = new DefaultExchange(camelContext);
        kept.getMessage().setBody(body);
        transformer.transform(kept.getMessage(), DataType.ANY, DataType.ANY);
        Assertions.assertSame(body, kept.getMessage().getBody());
        // a Map is no Json
        Exchange other = new DefaultExchange(camelContext);
        other.getMessage().setBody(Map.of("name", "Goofy"));
        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
    }
}
