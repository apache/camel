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
package org.apache.camel.spring;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.MDC;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringMDCTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/SpringMDCTest.xml");
    }

    @Test
    public void testMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMDCTwoMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:a", "Hello World");
        template.sendBody("direct:a", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public static class ProcessorA implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            assertEquals("route-a", MDC.get("camel.routeId"));
            assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
        }
    }

    public static class ProcessorB implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            assertEquals("route-b", MDC.get("camel.routeId"));
            assertEquals(exchange.getExchangeId(), MDC.get("camel.exchangeId"));
        }
    }

}
