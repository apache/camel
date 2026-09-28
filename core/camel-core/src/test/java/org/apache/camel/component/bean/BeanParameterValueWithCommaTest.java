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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A comma inside a Simple expression in the method name, such as the arguments of an OGNL method call in
 * ${body.substring(0, 3)}, is part of that parameter and does not separate the parameters.
 */
class BeanParameterValueWithCommaTest extends ContextTestSupport {

    private final MyBean bean = new MyBean();
    private final MyOverloadedBean overloaded = new MyOverloadedBean();

    @Test
    void testOgnlMethodCallWithTwoArguments() {
        assertEquals("[abc]", template.requestBody("direct:substring", "abcdef"));
    }

    @Test
    void testTwoParametersWithCommaInSecond() {
        assertEquals("[X|bbb]", template.requestBodyAndHeader("direct:two", "X", "v", "aaa"));
    }

    @Test
    void testTwoParametersWithCommaInBoth() {
        assertEquals("[bc|a]", template.requestBody("direct:twoOgnl", "abcdef"));
    }

    @Test
    void testNestedParenthesis() {
        assertEquals("[bc]", template.requestBody("direct:chained", "abcdef"));
        assertEquals("[zzz|X]", template.requestBodyAndHeader("direct:nestedFunction", "X", "v", "aaa"));
    }

    @Test
    void testOverloadedMethod() {
        assertEquals("[abc]", template.requestBody("direct:overloaded", "abcdef"));
        assertEquals("[abc|de]", template.requestBody("direct:overloadedTwo", "abcdef"));
        assertEquals("[abc]", template.requestBody("direct:overloadedType", "abcdef"));
    }

    @Test
    void testExistingForms() {
        assertEquals("[abcdef]", template.requestBody("direct:body", "abcdef"));
        assertEquals("[X|aaa]", template.requestBodyAndHeader("direct:twoPlain", "X", "v", "aaa"));
        assertEquals("[a,b]", template.requestBody("direct:quotedComma", "Hello"));
        assertEquals("[a,b|(c, d)]", template.requestBody("direct:quotedTwo", "Hello"));
        assertEquals("[Hello|b]", template.requestBody("direct:bodyAndQuoted", "Hello"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:substring").bean(bean, "echo(${body.substring(0, 3)})");
                from("direct:two").bean(bean, "two(${body}, ${header.v.replace('a', 'b')})");
                from("direct:twoOgnl").bean(bean, "two(${body.substring(1, 3)}, ${body.substring(0, 1)})");
                from("direct:chained").bean(bean, "echo(${body.substring(0, 4).substring(1, 3)})");
                from("direct:nestedFunction").bean(bean, "two(${replace(a,z,${header.v})}, ${body})");
                from("direct:overloaded").bean(overloaded, "echo(${body.substring(0, 3)})");
                from("direct:overloadedTwo").bean(overloaded, "echo(${body.substring(0, 3)}, ${body.substring(3, 5)})");
                from("direct:overloadedType").bean(overloaded, "echo(String.class ${body.substring(0, 3)})");
                from("direct:body").bean(bean, "echo(${body})");
                from("direct:twoPlain").bean(bean, "two(${body}, ${header.v})");
                from("direct:quotedComma").bean(bean, "echo('a,b')");
                from("direct:quotedTwo").bean(bean, "two('a,b', '(c, d)')");
                from("direct:bodyAndQuoted").bean(bean, "two(${body}, 'b')");
            }
        };
    }

    public static class MyBean {

        public String echo(String s) {
            return "[" + s + "]";
        }

        public String two(String a, String b) {
            return "[" + a + "|" + b + "]";
        }
    }

    public static class MyOverloadedBean {

        public String echo(String s) {
            return "[" + s + "]";
        }

        public String echo(String a, String b) {
            return "[" + a + "|" + b + "]";
        }
    }
}
