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
package org.apache.camel.jsonpath;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonPathEngine} {@code writeAsString} handling.
 */
public class JsonPathEngineWriteAsStringTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HTTPBIN_JSON = """
            {
              "args": {
                "age": 30,
                "name": "Alice"
              },
              "content": [
                {"id": 1, "value": "first"},
                {"id": 2, "value": "second"}
              ]
            }
            """;

    @Test
    void writeAsStringSerializesObjectExpressionAsJsonString() throws Exception {
        JsonPathEngine engine = new JsonPathEngine("$.args", null, true, false, true, null, context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(HTTPBIN_JSON);

        Object result = engine.read(exchange);

        assertThat(result).isInstanceOf(String.class);
        assertThat(MAPPER.readTree((String) result)).isEqualTo(MAPPER.readTree("""
                {"age":30,"name":"Alice"}
                """));
    }

    @Test
    void writeAsStringWithoutFlagReturnsMap() throws Exception {
        JsonPathEngine engine = new JsonPathEngine("$.args", null, false, false, true, null, context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(HTTPBIN_JSON);

        Object result = engine.read(exchange);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map)
                .containsEntry("age", 30)
                .containsEntry("name", "Alice");
    }

    @Test
    void writeAsStringSerializesEachArrayElement() throws Exception {
        JsonPathEngine engine = new JsonPathEngine("$.content[*]", null, true, false, true, null, context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(HTTPBIN_JSON);

        Object result = engine.read(exchange);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> jsonRows = (List<String>) result;
        assertThat(jsonRows).hasSize(2);
        assertThat(MAPPER.readTree(jsonRows.get(0))).isEqualTo(MAPPER.readTree("""
                {"id":1,"value":"first"}
                """));
        assertThat(MAPPER.readTree(jsonRows.get(1))).isEqualTo(MAPPER.readTree("""
                {"id":2,"value":"second"}
                """));
    }
}
