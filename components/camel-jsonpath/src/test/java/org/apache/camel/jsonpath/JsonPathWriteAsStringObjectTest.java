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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code writeAsString=true} when the JsonPath expression evaluates to a single JSON object (Map).
 */
public class JsonPathWriteAsStringObjectTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HTTPBIN_JSON = """
            {
              "args": {
                "age": 30,
                "name": "Alice"
              },
              "headers": {
                "Host": "httpbin.org",
                "Accept": "application/json"
              },
              "origin": "178.227.111.11",
              "url": "https://httpbin.org/get?name=Alice&age=30"
            }
            """;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:header")
                        .setHeader("NewBody").jsonpathWriteAsString("$.args")
                        .to("mock:header");

                from("direct:body")
                        .setBody().jsonpathWriteAsString("$.args")
                        .to("mock:body");

                from("direct:nested")
                        .setBody().jsonpathWriteAsString("$.headers")
                        .to("mock:nested");
            }
        };
    }

    @Test
    void writeAsStringOnObjectExpressionInHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:header");
        mock.expectedMessageCount(1);

        template.sendBody("direct:header", HTTPBIN_JSON);

        MockEndpoint.assertIsSatisfied(context);

        String header = mock.getReceivedExchanges().get(0).getIn().getHeader("NewBody", String.class);
        assertThat(header).isNotNull();
        assertJsonEquals(header, """
                {"age":30,"name":"Alice"}
                """);
    }

    @Test
    void writeAsStringOnObjectExpressionInBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedMessageCount(1);

        template.sendBody("direct:body", HTTPBIN_JSON);

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertThat(body).isNotNull();
        assertJsonEquals(body, """
                {"age":30,"name":"Alice"}
                """);
    }

    @Test
    void writeAsStringOnNestedObjectExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:nested");
        mock.expectedMessageCount(1);

        template.sendBody("direct:nested", HTTPBIN_JSON);

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertThat(body).isNotNull();
        assertJsonEquals(body, """
                {"Host":"httpbin.org","Accept":"application/json"}
                """);
    }

    @Test
    void writeAsStringDoesNotReturnMapToString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:body");
        mock.expectedMessageCount(1);

        template.sendBody("direct:body", HTTPBIN_JSON);

        MockEndpoint.assertIsSatisfied(context);

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertThat(body)
                .startsWith("{")
                .endsWith("}")
                .doesNotContain("=");
    }

    private static void assertJsonEquals(String actual, String expected) throws Exception {
        assertThat(MAPPER.readTree(actual)).isEqualTo(MAPPER.readTree(expected));
    }
}
