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
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A parameter value in the method name that is a Simple expression, such as ${body} or ${header.foo}, is passed to the
 * method as-is. Only the quotes of a quoted literal are removed, and only the literal null is a null value.
 */
class BeanParameterValueFromExpressionTest extends ContextTestSupport {

    private final MyBean bean = new MyBean();

    @Test
    void testQuotedHeaderValue() {
        assertEquals("[\"33a64df5\"]", template.requestBodyAndHeader("direct:header", "Hello", "v", "\"33a64df5\""));
        assertEquals("['abc']", template.requestBodyAndHeader("direct:header", "Hello", "v", "'abc'"));
        assertEquals("[  'x'  ]", template.requestBodyAndHeader("direct:header", "Hello", "v", "  'x'  "));
        assertEquals("['abc']", template.requestBodyAndHeader("direct:headerUri", "Hello", "v", "'abc'"));
    }

    @Test
    void testQuotedBody() {
        assertEquals("[\"ACME, Inc.\",\"42\"]", template.requestBody("direct:body", "\"ACME, Inc.\",\"42\""));
        assertEquals("['']", template.requestBody("direct:body", "''"));
    }

    @Test
    void testQuotedHeaderValues() {
        assertEquals("['x'|\"y\"]", template.requestBody("direct:two", "Hello"));
    }

    @Test
    void testNullText() {
        // the text null is not the null keyword
        assertEquals("[null]", template.requestBodyAndHeader("direct:header", "Hello", "v", "null"));
        // but an expression that evaluates to null is still passed as null
        assertEquals("<null>", template.requestBody("direct:header", "Hello"));
    }

    @Test
    void testLiterals() {
        assertEquals("[abc]", template.requestBodyAndHeader("direct:header", "Hello", "v", "abc"));
        assertEquals("[World]", template.requestBody("direct:single", "Hello"));
        assertEquals("[World]", template.requestBody("direct:double", "Hello"));
        assertEquals("<null>", template.requestBody("direct:null", "Hello"));
        assertEquals("[null]", template.requestBody("direct:quotedNull", "Hello"));
        assertEquals("[\"abc\"]", template.requestBodyAndHeader("direct:quotedHeader", "Hello", "v", "\"abc\""));
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:header").bean(bean, "echo(${header.v})");
                from("direct:headerUri").to("bean:foo?method=echo(${header.v})");
                from("direct:body").bean(bean, "echo(${body})");
                from("direct:two").setHeader("a", constant("'x'")).setHeader("b", constant("\"y\""))
                        .bean(bean, "two(${header.a}, ${header.b})");
                from("direct:single").bean(bean, "echo('World')");
                from("direct:double").bean(bean, "echo(\"World\")");
                from("direct:null").bean(bean, "echo(null)");
                from("direct:quotedNull").bean(bean, "echo('null')");
                from("direct:quotedHeader").bean(bean, "echo('${header.v}')");
            }
        };
    }

    public static class MyBean {

        public String echo(String s) {
            return s == null ? "<null>" : "[" + s + "]";
        }

        public String two(String a, String b) {
            return "[" + a + "|" + b + "]";
        }
    }
}
