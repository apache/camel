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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.jackson3.SchemaHelper;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import tools.jackson.databind.JsonNode;

class JsonDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final JsonDataTypeTransformer transformer = new JsonDataTypeTransformer();

    @Test
    void shouldHandleJsonString() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody("""
                    { "name": "Christoph", "age": 32 }
                """);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    { "name": "Christoph", "age": 32 }
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldHandlePojo() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new Person("Mickey", 20));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

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

        JSONAssert.assertEquals("""
                    {"name":"Donald","age":19}
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    public void shouldLookupDataTypeTransformer() throws Exception {
        Transformer transformer = camelContext.getTransformerRegistry()
                .resolveTransformer(new TransformerKey("application-json"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(JsonDataTypeTransformer.class, transformer.getClass());
    }

    @Test
    void shouldHandleMapAndList() throws Exception {
        // CAMEL-26300: a Map or a List has no class of its own, and is written as a Json object or array
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Goofy");
        map.put("age", 25);
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(map);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    {"name":"Goofy","age":25}
                """, exchange.getMessage().getBody(String.class), true);

        exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(List.of(new Person("Goofy", 25), Map.of("name", "Mickey")));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JSONAssert.assertEquals("""
                    [{"name":"Goofy","age":25},{"name":"Mickey"}]
                """, exchange.getMessage().getBody(String.class), true);
    }

    @Test
    void shouldHandleJsonAsBytesOrStream() throws Exception {
        // CAMEL-26300: Json as bytes, a stream, a reader or the stream cache is kept as it is
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (Object body : List.of(bytes, new ByteArrayInputStream(bytes), new StringReader(json),
                new InputStreamCache(bytes))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            Assertions.assertInstanceOf(byte[].class, exchange.getMessage().getBody(), body.getClass().getName());
            Assertions.assertEquals(json, exchange.getMessage().getBody(String.class), body.getClass().getName());
        }
    }

    @Test
    void shouldWriteOtherTextAsJsonString() throws Exception {
        // CAMEL-26300: text that is no Json object or array, as a String or as bytes, becomes a Json string
        String text = "Hello \"Goofy\"";
        for (Object body : List.of(text, text.getBytes(StandardCharsets.UTF_8))) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            Assertions.assertEquals("\"Hello \\\"Goofy\\\"\"", exchange.getMessage().getBody(String.class),
                    body.getClass().getName());
            Assertions.assertEquals(text, Json.mapper().readTree(exchange.getMessage().getBody(byte[].class)).asString());
        }
    }

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: Json as text, bytes or a stream, other text, a Map, a List, a JsonNode, a POJO or a null body,
        // as transformerIn says, and the Json bytes transformerOut says
        BodyTypes bodyTypes = JsonDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        Assertions.assertEquals(
                List.of(Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.NULL),
                List.of(bodyTypes.transformerIn().value()));
        Assertions.assertEquals(List.of(Carrier.BYTES), List.of(bodyTypes.transformerOut().value()));
        Assertions.assertEquals(byte[].class.getCanonicalName(), bodyTypes.transformerOut().javaType());
        Assertions.assertEquals("json", bodyTypes.transformerOut().format());
        String json = "{ \"name\": \"Goofy\", \"age\": 25 }";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (Object body : Arrays.asList(json, bytes, new ByteArrayInputStream(bytes), "Goofy", Map.of("name", "Goofy"),
                List.of("Goofy"), Json.mapper().readerFor(JsonNode.class).readValue(json), new Person("Goofy", 25), null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            Assertions.assertInstanceOf(byte[].class, exchange.getMessage().getBody(), String.valueOf(body));
        }
        // with the X-Content-Class property only a body of that class
        Exchange other = new DefaultExchange(camelContext);
        other.setProperty(SchemaHelper.CONTENT_CLASS, Person.class.getName());
        other.getMessage().setBody(Map.of("name", "Goofy"));
        Assertions.assertThrows(CamelExecutionException.class,
                () -> transformer.transform(other.getMessage(), DataType.ANY, DataType.ANY));
    }
}
