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
package org.apache.camel.example;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FallbackTypeConverterDoesNotThrowExceptionTest extends CamelTestSupport {

    @Test
    public void testJaxbModel() throws InterruptedException {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody("direct:a", new Foo());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoneJaxbModel() throws InterruptedException {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody("direct:a", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAnotherJaxbModel() throws InterruptedException {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody("direct:a", new Bar());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().getBody(Foo.class);
                    }
                }).to("mock:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().getBody(List.class);
                    }
                }).to("mock:b");
            }
        };
    }
}