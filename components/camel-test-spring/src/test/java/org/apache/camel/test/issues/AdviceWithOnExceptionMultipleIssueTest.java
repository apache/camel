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
package org.apache.camel.test.issues;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 */
public class AdviceWithOnExceptionMultipleIssueTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/test/issues/AdviceWithOnExceptionMultipleIssueTest.xml");
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testSimpleMultipleAdvice() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("RouteA"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                    }
                });
            }
        });

        RouteReifier.adviceWith(context.getRouteDefinition("RouteB"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(1);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleAdviceWithExceptionThrown() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("RouteA"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new Exception("my exception");
                    }
                });
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(0);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleAdvice() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinition("RouteA"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new Exception("my exception");
                    }
                });
            }
        });

        RouteReifier.adviceWith(context.getRouteDefinition("RouteB"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(0);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

}
