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
package org.apache.camel.dataformat.toon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToonDataFormatTest extends CamelTestSupport {

    @Test
    void marshalAndUnmarshalMap() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", 1);
        input.put("name", "Ada");

        String toon = template.requestBody("direct:marshal", input, String.class);
        assertThat(toon).isNotBlank();

        Object result = template.requestBody("direct:unmarshal", toon);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("Ada");
        assertNumberEquals(map.get("id"), 1);
    }

    @Test
    void marshalAndUnmarshalList() {
        List<String> input = List.of("red", "green", "blue");

        String toon = template.requestBody("direct:marshal", input, String.class);
        Object result = template.requestBody("direct:unmarshal", toon);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> colors = (List<String>) result;
        assertThat(colors).containsExactly("red", "green", "blue");
    }

    @Test
    void marshalAndUnmarshalNestedObject() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "London");
        address.put("zip", 12345);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "Ada");
        input.put("address", address);

        Object result = template.requestBody("direct:roundtrip", input);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("Ada");
        assertThat(map.get("address")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) map.get("address");
        assertThat(nested.get("city")).isEqualTo("London");
        assertNumberEquals(nested.get("zip"), 12345);
    }

    @Test
    void marshalAndUnmarshalPrimitives() {
        assertThat(template.requestBody("direct:roundtrip", true)).isEqualTo(true);
        assertThat(template.requestBody("direct:roundtrip", false)).isEqualTo(false);
        assertThat(template.requestBody("direct:roundtrip", "\"hello\"")).isEqualTo("hello");
        assertNumberEquals(template.requestBody("direct:roundtrip", 42), 42);
        Object decimal = template.requestBody("direct:roundtrip", 1.5);
        assertThat(decimal).isInstanceOf(Number.class);
        assertThat(((Number) decimal).doubleValue()).isEqualTo(1.5);
    }

    @Test
    void marshalAndUnmarshalNull() {
        String toon = template.requestBody("direct:marshal", null, String.class);
        assertThat(toon).isNotNull();
        assertThat(template.requestBody("direct:unmarshal", toon)).isNull();
    }

    @Test
    void marshalJsonStringAsDocument() {
        String json = "{\"id\":1,\"name\":\"Ada\"}";

        String toon = template.requestBody("direct:marshal", json, String.class);
        assertThat(toon).doesNotStartWith("\"");
        assertThat(toon).contains("Ada");

        Object result = template.requestBody("direct:unmarshal", toon);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("Ada");
        assertNumberEquals(map.get("id"), 1);
    }

    @Test
    void roundTripPreservesSemanticValues() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("count", 3);
        input.put("active", true);
        input.put("tags", List.of("a", "b"));

        Object result = template.requestBody("direct:roundtrip", input);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertNumberEquals(map.get("count"), 3);
        assertThat(map.get("active")).isEqualTo(true);
        assertThat(map.get("tags")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) map.get("tags");
        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void marshalPrimitiveArray() {
        Object result = template.requestBody("direct:roundtrip", new int[] { 1, 2, 3 });
        assertThat(result).isInstanceOf(List.class);
        List<?> list = (List<?>) result;
        assertThat(list).hasSize(3);
        assertNumberEquals(list.get(0), 1);
        assertNumberEquals(list.get(1), 2);
        assertNumberEquals(list.get(2), 3);
    }

    @Test
    void marshalUniformObjectArray() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of("id", 1, "name", "Ada"));
        input.add(Map.of("id", 2, "name", "Grace"));

        Object result = template.requestBody("direct:roundtrip", input);
        assertThat(result).isInstanceOf(List.class);
        List<?> list = (List<?>) result;
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        assertThat(first.get("name")).isEqualTo("Ada");
        assertNumberEquals(first.get("id"), 1);
    }

    @Test
    void marshalEmptyArray() {
        Object result = template.requestBody("direct:roundtrip", List.of());
        assertThat(result).isInstanceOf(List.class);
        assertThat((List<?>) result).isEmpty();
    }

    @Test
    void roundTripEscapedStrings() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("comma", "red,green");
        input.put("tab", "left\tright");
        input.put("pipe", "a|b");
        input.put("quotes", "say \"hello\"");
        input.put("newline", "line1\nline2");
        input.put("unicode", "café 日本語");

        Object result = template.requestBody("direct:roundtrip", input);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("comma")).isEqualTo("red,green");
        assertThat(map.get("tab")).isEqualTo("left\tright");
        assertThat(map.get("pipe")).isEqualTo("a|b");
        assertThat(map.get("quotes")).isEqualTo("say \"hello\"");
        assertThat(map.get("newline")).isEqualTo("line1\nline2");
        assertThat(map.get("unicode")).isEqualTo("café 日本語");
    }

    @Test
    void delimiterOptionUsesPipe() {
        String toon = template.requestBody("direct:pipe", List.of("a", "b", "c"), String.class);
        assertThat(toon).contains("|");
        Object result = template.requestBody("direct:unmarshalPipe", toon);
        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) result;
        assertThat(values).containsExactly("a", "b", "c");
    }

    @Test
    void indentOptionUsesFourSpaces() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", "value");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("outer", nested);

        String toon = template.requestBody("direct:indent", input, String.class);
        assertThat(toon).contains("    inner");
    }

    @Test
    void lengthMarkerOptionPrefixesArrayLength() {
        String toon = template.requestBody("direct:lengthMarker", List.of("a", "b"), String.class);
        assertThat(toon).contains("[#");
    }

    @Test
    void contentTypeHeaderIsSetByDefault() {
        Exchange exchange = template.request("direct:marshal", e -> e.getIn().setBody(Map.of("ok", true)));
        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/toon");
    }

    @Test
    void contentTypeHeaderCanBeDisabled() {
        Exchange exchange = template.request("direct:noContentType", e -> e.getIn().setBody(Map.of("ok", true)));
        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isNull();
    }

    @Test
    void invalidJsonStringMarshalFails() {
        assertThatThrownBy(() -> template.requestBody("direct:marshal", "not-json", String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void malformedToonFailsInStrictMode() {
        assertThatThrownBy(() -> template.requestBody("direct:unmarshal", "tags[2]: a,b,c", Object.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strictFalseUsesBestEffortParsing() {
        Object result = template.requestBody("direct:strictFalse", "tags[2]: a,b,c");
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("tags")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) map.get("tags");
        assertThat(tags).containsExactly("a", "b", "c");
    }

    @Test
    void emptyJsonStringMarshalFails() {
        assertThatThrownBy(() -> template.requestBody("direct:marshal", "", String.class))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyToonUnmarshalsToEmptyObject() {
        Object result = template.requestBody("direct:unmarshal", "");
        assertThat(result).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) result).isEmpty();
    }

    @Test
    void marshalPojo() {
        Person person = new Person("Ada", 36);
        Object result = template.requestBody("direct:roundtrip", person);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertThat(map.get("name")).isEqualTo("Ada");
        assertNumberEquals(map.get("age"), 36);
    }

    private static void assertNumberEquals(Object actual, long expected) {
        assertThat(actual).isInstanceOf(Number.class);
        assertThat(((Number) actual).longValue()).isEqualTo(expected);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal").marshal().toon();
                from("direct:unmarshal").unmarshal().toon();
                from("direct:roundtrip").marshal().toon().unmarshal().toon();
                from("direct:pipe").marshal(dataFormat().toon().delimiter("PIPE").end());
                from("direct:unmarshalPipe").unmarshal(dataFormat().toon().delimiter("PIPE").end());
                from("direct:indent").marshal(dataFormat().toon().indent(4).end());
                from("direct:lengthMarker").marshal(dataFormat().toon().lengthMarker(true).end());
                from("direct:noContentType").marshal(dataFormat().toon().contentTypeHeader(false).end());
                from("direct:strictFalse").unmarshal(dataFormat().toon().strict(false).end());
            }
        };
    }

    public static class Person {
        private String name;
        private int age;

        public Person() {
        }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
