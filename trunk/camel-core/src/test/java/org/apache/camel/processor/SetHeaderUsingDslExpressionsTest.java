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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExpressionAdapter;

/**
 * @version 
 */
public class SetHeaderUsingDslExpressionsTest extends ContextTestSupport {
    protected String body = "<person name='James' city='London'/>";
    protected MockEndpoint expected;
    
    public final class MyValueClass {
        
        private String value1;
        private String value2;
        
        public MyValueClass(String v1, String v2) {
            value1 = v1;
            value2 = v2;
        }
        
        public int hashCode() {
            return value1.hashCode() * 10 + value2.hashCode();
        }
        
        public boolean equals(Object obj) {
            boolean result = false;        
            if (obj instanceof MyValueClass) {
                MyValueClass value = (MyValueClass)obj;
                if (this.value1.equals(value.value1) && this.value2.equals(value.value2)) {
                    result = true;
                }
            } 
            return result;
        }
        
    }

    public void testUseConstant() throws Exception {
        MyValueClass value = new MyValueClass("value1", "value2");
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                MyValueClass insteadValue = new MyValueClass("value1", "value2");
                from("direct:start").
                        setHeader("foo").constant("ABC").
                        setHeader("value").constant(insteadValue).
                        to("mock:result");
            }
        });
        
        expected.message(0).header("value").isEqualTo(value);

        template.sendBodyAndHeader("direct:start", body, "bar", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testUseConstantParameter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                        setHeader("foo", constant("ABC")).
                        to("mock:result");
            }
        });

        template.sendBodyAndHeader("direct:start", body, "bar", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testUseExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").setHeader("foo").expression(new ExpressionAdapter() {
                    public Object evaluate(Exchange exchange) {
                        return "ABC";
                    }
                }).to("mock:result");
            }
        });

        template.sendBodyAndHeader("direct:start", body, "bar", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testUseHeaderExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                        setHeader("foo").header("bar").
                        to("mock:result");
            }
        });

        template.sendBodyAndHeader("direct:start", body, "bar", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testUseHeaderXpathExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                    setHeader("foo").xpath("/personFile/text()").
                    to("mock:result");
            }
        });

        template.sendBody("direct:start", "<personFile>ABC</personFile>");

        assertMockEndpointsSatisfied();
    }

    public void testUseBodyExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                        setHeader("foo").body().
                        to("mock:result");
            }
        });

        template.sendBody("direct:start", "ABC");

        assertMockEndpointsSatisfied();
    }

    public void testUseBodyAsTypeExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                        setHeader("foo").body(String.class).
                        to("mock:result");
            }
        });

        template.sendBody("direct:start", "ABC".getBytes());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        expected = getMockEndpoint("mock:result");
        expected.message(0).header("foo").isEqualTo("ABC");
    }
}
