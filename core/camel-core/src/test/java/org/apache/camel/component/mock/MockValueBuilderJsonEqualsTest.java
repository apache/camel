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
package org.apache.camel.component.mock;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JSON comparison functionality in MockValueBuilder.
 */
public class MockValueBuilderJsonEqualsTest extends ContextTestSupport {

    @Test
    public void testJsonObjectWithDifferentKeyOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\",\"age\":30}");

        // Send JSON with different key order
        template.sendBody("direct:start", "{\"age\":30,\"name\":\"John\"}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayWithDifferentElementOrderIgnored() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[1,2,3]", true);

        // Send array with different order
        template.sendBody("direct:start", "[3,2,1]");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayWithDifferentElementOrderStrict() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[1,2,3]", false);

        // Send array with same order
        template.sendBody("direct:start", "[1,2,3]");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayWithDifferentElementOrderStrictFails() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[1,2,3]", false);

        // Send array with different order - should fail
        template.sendBody("direct:start", "[3,2,1]");

        assertThrows(Throwable.class, this::assertMockEndpointsSatisfied);
    }

    @Test
    public void testNestedJsonObjectsWithDifferentOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        String expected = "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"NYC\"}";
        mock.message(0).body().jsonEquals(expected);

        // Send with different order at multiple levels
        String actual = "{\"city\":\"NYC\",\"person\":{\"age\":30,\"name\":\"John\"}}";
        template.sendBody("direct:start", actual);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayOfObjectsWithDifferentOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        String expected = "[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]";
        mock.message(0).body().jsonEquals(expected, true);

        // Send with different array order
        String actual = "[{\"id\":2,\"name\":\"B\"},{\"id\":1,\"name\":\"A\"}]";
        template.sendBody("direct:start", actual);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testComplexNestedStructure() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        String expected = "{\"items\":[{\"id\":1,\"tags\":[\"a\",\"b\"]},{\"id\":2,\"tags\":[\"c\",\"d\"]}],\"total\":2}";
        mock.message(0).body().jsonEquals(expected, true);

        // Send with different orders at multiple levels
        String actual = "{\"total\":2,\"items\":[{\"tags\":[\"b\",\"a\"],\"id\":1},{\"tags\":[\"d\",\"c\"],\"id\":2}]}";
        template.sendBody("direct:start", actual);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonWithNullValues() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\",\"age\":null}");

        template.sendBody("direct:start", "{\"age\":null,\"name\":\"John\"}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonWithBooleanValues() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"active\":true,\"verified\":false}");

        template.sendBody("direct:start", "{\"verified\":false,\"active\":true}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonWithNumberValues() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"count\":42,\"price\":19.99,\"score\":-5}");

        template.sendBody("direct:start", "{\"score\":-5,\"count\":42,\"price\":19.99}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayOfPrimitives() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[\"apple\",\"banana\",\"cherry\"]", true);

        template.sendBody("direct:start", "[\"cherry\",\"apple\",\"banana\"]");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayOfPrimitivesStrictOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[\"apple\",\"banana\",\"cherry\"]", false);

        template.sendBody("direct:start", "[\"apple\",\"banana\",\"cherry\"]");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyJsonObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{}");

        template.sendBody("direct:start", "{}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEmptyJsonArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[]");

        template.sendBody("direct:start", "[]");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonMismatchFails() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\"}");

        template.sendBody("direct:start", "{\"name\":\"Jane\"}");

        assertThrows(Throwable.class, this::assertMockEndpointsSatisfied);
    }

    @Test
    public void testJsonDifferentStructureFails() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\"}");

        template.sendBody("direct:start", "{\"name\":\"John\",\"age\":30}");

        assertThrows(Throwable.class, this::assertMockEndpointsSatisfied);
    }

    @Test
    public void testJsonArrayDifferentSizeFails() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[1,2,3]");

        template.sendBody("direct:start", "[1,2]");

        assertThrows(Throwable.class, this::assertMockEndpointsSatisfied);
    }

    @Test
    public void testByteArrayInput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\"}");

        template.sendBody("direct:start", "{\"name\":\"John\"}".getBytes());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonWithWhitespace() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("{\"name\":\"John\",\"age\":30}");

        // Send with extra whitespace
        template.sendBody("direct:start", "{\n  \"age\": 30,\n  \"name\": \"John\"\n}");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testJsonArrayWithMixedTypes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().jsonEquals("[1,\"text\",true,null,{\"key\":\"value\"}]", true);

        template.sendBody("direct:start", "[{\"key\":\"value\"},null,true,\"text\",1]");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
}
