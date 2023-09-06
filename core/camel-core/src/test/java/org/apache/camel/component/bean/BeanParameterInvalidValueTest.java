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

import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class BeanParameterInvalidValueTest extends ContextTestSupport {

    @Test
    public void testBeanParameterInvalidValueA() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:a", "World"),
                "Should have thrown exception");

        TypeConversionException cause = assertIsInstanceOf(TypeConversionException.class, e.getCause().getCause());
        assertEquals(String.class, cause.getFromType());
        assertEquals(int.class, cause.getToType());
        assertEquals("A", cause.getValue());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanParameterInvalidValueB() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:b", "World"),
                "Should have thrown exception");

        TypeConversionException cause = assertIsInstanceOf(TypeConversionException.class, e.getCause().getCause());
        assertEquals(String.class, cause.getFromType());
        assertEquals(int.class, cause.getToType());
        assertEquals("true", cause.getValue());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanParameterNullC() throws Exception {
        // should be an empty string
        getMockEndpoint("mock:result").expectedBodiesReceived("");

        template.sendBody("direct:c", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanParameterInvalidValueD() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:d", "World"),
                "Should have thrown exception");

        ExpressionEvaluationException cause = assertIsInstanceOf(ExpressionEvaluationException.class, e.getCause());
        assertTrue(cause.getCause().getMessage().startsWith("Unknown function: xxx at location 0"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").to("bean:foo?method=echo(*, 'A')").to("mock:result");

                from("direct:b").to("bean:foo?method=echo(*, true)").to("mock:result");

                from("direct:c").to("bean:foo?method=echo(null, 2)").to("mock:result");

                from("direct:d").to("bean:foo?method=echo(${xxx}, 2)").to("mock:result");
            }
        };
    }

    public static class MyBean {

        public String echo(String body, int times) {
            if (body == null) {
                // use an empty string for no body
                return "";
            }

            if (times > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < times; i++) {
                    sb.append(body);
                }
                return sb.toString();
            }

            return body;
        }

        public String heads(String body, Map<?, ?> headers) {
            return headers.get("hello") + " " + body;
        }

    }
}
