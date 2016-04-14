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
package org.apache.camel.component.hystrix;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HystrixComponentTimeOutTest extends CamelTestSupport {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @EndpointInject(uri = "mock:error")
    protected MockEndpoint errorEndpoint;

    @Test
    public void slowOperationTimesOutAndFallbacks() throws Exception {
        resultEndpoint.expectedMessageCount(0);
        errorEndpoint.expectedMessageCount(1);

        template.sendBody("test");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void slowOperationSucceedsWithExtendedTimeout() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        errorEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("test", HystrixConstants.CAMEL_HYSTRIX_EXECUTION_TIMEOUT_IN_MILLISECONDS, Integer.valueOf(700));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        CamelContext context = new DefaultCamelContext(registry);
        registry.put("run", context.getEndpoint("direct:run"));
        registry.put("fallback", context.getEndpoint("direct:fallback"));
        registry.put("headerExpression", ExpressionBuilder.headerExpression("key"));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {

                from("direct:fallback")
                        .to("mock:error");

                from("direct:run")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Thread.sleep(500); //a slow operation
                            }
                        })
                        .to("mock:result");

                from("direct:start")
                        .to("hystrix:testKey?runEndpoint=direct:run&fallbackEndpoint=direct:fallback&executionTimeoutInMilliseconds=100");
            }
        };
    }
}

