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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlElement;

import com.jayway.jsonpath.InvalidJsonException;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonPathJacksonTest extends CamelTestSupport {

    private static final String JSON = "{\"a\":{\"b\":1},\"arr\":[1,2],\"s\":\"t\"}";

    private final com.fasterxml.jackson.databind.ObjectMapper jackson2 = new com.fasterxml.jackson.databind.ObjectMapper();
    private final ObjectMapper jackson3 = new ObjectMapper();

    private Object read(Object body, String path, boolean writeAsString) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return new JsonPathEngine(path, null, writeAsString, false, true, null, context).read(exchange);
    }

    @Test
    void jackson2NodeBodyIsReadAsJsonNotAsBean() throws Exception {
        assertThat(read(jackson2.readTree(JSON), "$.a.b", false)).isEqualTo(1);
        assertThat(read(jackson2.readTree(JSON), "$", false))
                .isEqualTo(Map.of("a", Map.of("b", 1), "arr", List.of(1, 2), "s", "t"));
        assertThat(read(jackson2.readTree("[1,2]"), "$[1]", false)).isEqualTo(2);
        assertThat(read(jackson2.readTree(JSON), "$.a", true)).isEqualTo("{\"b\":1}");
    }

    @Test
    void jackson3NodeBody() throws Exception {
        assertThat(read(jackson3.readTree(JSON), "$.a.b", false)).isEqualTo(1);
        assertThat(read(jackson3.readTree("[1,2]"), "$[1]", false)).isEqualTo(2);
        assertThat(read(jackson3.readTree(JSON), "$.a", true)).isEqualTo("{\"b\":1}");
    }

    @Test
    void jackson2NodeResultIsWrittenAsJson() throws Exception {
        // the node is an Iterable of its values, which are written one by one
        assertThat(read(Map.of("n", jackson2.readTree(JSON)), "$.n", true))
                .isEqualTo(List.of("{\"b\":1}", "[1,2]", "\"t\""));
    }

    @Test
    void unpackArrayOfJackson2NodeBody() throws Exception {
        JsonPathExpression expression = new JsonPathExpression("$.arr[?(@ == 2)]");
        expression.setResultType(Integer.class);
        expression.setUnpackArray(true);
        expression.init(context);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(jackson2.readTree(JSON));

        assertThat(expression.evaluate(exchange)).isEqualTo(2);
    }

    @Test
    void pojoIsWrittenWithJackson2Defaults() throws Exception {
        assertThat(read(new Sample(), "$.date", false)).isEqualTo(1000L);
        assertThat(read(new Sample(), "$.color", false)).isEqualTo("RED");
        // declared order, date as a number and enum by name
        assertThat(read(new Sample(), "$", true)).isEqualTo("{\"zeta\":\"z\",\"alpha\":\"a\",\"date\":1000,\"color\":\"RED\"}");
    }

    @Test
    void contentAfterFirstValueIsIgnored() throws Exception {
        assertThat(read("{\"a\":1} x", "$.a", false)).isEqualTo(1);
        assertThat(read(stream("{\"a\":1} x"), "$.a", false)).isEqualTo(1);
    }

    @Test
    void invalidJsonFailsWithInvalidJsonException() {
        assertThatThrownBy(() -> read("{\"a\":", "$.a", false)).isInstanceOf(InvalidJsonException.class);
        assertThatThrownBy(() -> read(stream("{\"a\":"), "$.a", false)).isInstanceOf(InvalidJsonException.class);
    }

    @Test
    void jakartaXmlBindAnnotationsAreUsedWhenTheModuleIsPresent() throws Exception {
        assertThat(read(new Annotated(), "$.renamed", false)).isEqualTo("v");
    }

    @Test
    void registeredMapperIsUsed() throws Exception {
        context.getRegistry().bind("mapper", new CustomObjectMapper());
        assertThat(read("{\"f\":1.5}", "$.f", false)).isEqualTo(new BigDecimal("1.5"));
    }

    @Test
    void registeredJackson2MapperIsNotUsed() throws Exception {
        context.getRegistry().bind("mapper", jackson2.copy()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
        assertThat(read("{\"f\":1.5}", "$.f", false)).isEqualTo(1.5d);
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    public enum Color {
        RED {
            @Override
            public String toString() {
                return "red";
            }
        }
    }

    public static class Sample {
        public String zeta = "z";
        public String alpha = "a";
        public Date date = new Date(1000L);
        public Color color = Color.RED;
    }

    public static class Annotated {
        @XmlElement(name = "renamed")
        public String orig = "v";
    }
}
