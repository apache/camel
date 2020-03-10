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
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.Test;

public class CustomParameterMappingStrategyTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        jndi.bind(BeanConstants.BEAN_PARAMETER_MAPPING_STRATEGY, new MyCustomStrategy());
        return jndi;
    }

    @Test
    public void testExchange() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("You said: Hello Claus");
        template.sendBody("direct:a", "Claus");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").bean("foo").to("mock:result");
            }
        };
    }

    public static class MyFooBean {
        public String cheese(String body) {
            return "You said: " + body;
        }
    }

    public static class MyCustomStrategy implements ParameterMappingStrategy {

        @Override
        public Expression getDefaultParameterTypeExpression(Class<?> parameterType) {
            if (String.class.isAssignableFrom(parameterType)) {
                return new ExpressionAdapter() {
                    @Override
                    public Object evaluate(Exchange exchange) {
                        return "Hello " + exchange.getIn().getBody(String.class);
                    }
                };
            }
            return null;
        }
    }
}
