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
package org.apache.camel.processor;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class ExpressionPlaceholderNestedTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Properties myProp = new Properties();
        myProp.put("query", "{\"query\":{\"match_all\":{}}}");
        myProp.put("queryEscaped", "{\"query\":{\"match_all\":{}\\}}");

        context.getPropertiesComponent().setInitialProperties(myProp);

        return context;
    }

    @Test
    public void testPlaceholderFalse() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("{\"query\":{\"match_all\":{}}}");

        template.sendBody("direct:off", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPlaceholderOn() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:on")
                            .setBody().constant("{{query?nested=true}}")
                            .to("mock:result");
                }
            });
            fail();
        } catch (FailedToCreateRouteException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }
    }

    @Test
    public void testPlaceholderEscaped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("{\"query\":{\"match_all\":{}}}");

        template.sendBody("direct:escaped", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:off")
                        .setBody().constant("{{query?nested=false}}")
                        .to("mock:result");

                from("direct:escaped")
                        .setBody().constant("{{queryEscaped}}")
                        .to("mock:result");
            }
        };
    }
}
