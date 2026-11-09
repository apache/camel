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
package org.apache.camel.component.jsonata;

import java.util.Date;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The internal mapper keeps the Jackson 2 defaults, and a Jackson 2 node is still JSON input.
 */
class JsonataJacksonBindingTest extends CamelTestSupport {

    public enum Colour {
        RED;

        @Override
        public String toString() {
            return "red";
        }
    }

    public static class Bean {
        public String z = "z";
        public String a = "a";
        public Date date = new Date(0);
        public Colour colour = Colour.RED;
    }

    @Test
    void jackson2NodeIsConvertedAsJson() throws Exception {
        com.fasterxml.jackson.databind.JsonNode node
                = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"name\":\"Camel\"}");
        assertEquals("{\"fullName\":\"Camel\"}", template.requestBody("direct:fullName", node, String.class));
    }

    @Test
    void outputIsAJackson3Node() {
        Object out = template.requestBody("direct:node", "{\"name\":\"Camel\"}");
        assertEquals("Camel", assertInstanceOf(JsonNode.class, out).get("fullName").asString());
        assertEquals("{\"fullName\":\"Camel\"}",
                template.requestBody("direct:node", "{\"name\":\"Camel\"}", String.class));
    }

    @Test
    void beanKeepsPropertyOrderAndTimestampAndEnumName() {
        assertEquals("{\"z\":\"z\",\"a\":\"a\",\"date\":0,\"colour\":\"RED\"}",
                template.requestBody("direct:identity", new Bean(), String.class));
    }

    @Test
    void contentAfterTheFirstValueIsIgnored() {
        assertEquals("{\"fullName\":\"Camel\"}",
                template.requestBody("direct:fullName", "{\"name\":\"Camel\"}{\"name\":\"Other\"}", String.class));
    }

    @Test
    void prettyPrintKeepsItsFormat() {
        assertEquals("{\n  \"fullName\" : \"Camel\"\n}",
                template.requestBody("direct:pretty", "{\"name\":\"Camel\"}", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String fullName = "jsonata:org/apache/camel/component/jsonata/jacksonInput/fullName/expressions.spec";
                from("direct:fullName").to(fullName + "?outputType=JsonString");
                from("direct:node").to(fullName);
                from("direct:pretty").to(fullName + "?outputType=JsonString&prettyPrint=true");
                from("direct:identity")
                        .to("jsonata:org/apache/camel/component/jsonata/jacksonBinding/expressions.spec?outputType=JsonString");
            }
        };
    }
}
