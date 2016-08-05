/**
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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

public class BeanParameterInvalidSyntaxTest extends ContextTestSupport {

    public void testBeanParameterInvalidSyntax() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:a", "World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Method should have even pair of parenthesis, was echo(${body}, 5))", iae.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                    // invalid due extra parenthesis at the end
                    .to("bean:foo?method=echo(${body}, 5))")
                    .to("mock:result");
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

    }
}
