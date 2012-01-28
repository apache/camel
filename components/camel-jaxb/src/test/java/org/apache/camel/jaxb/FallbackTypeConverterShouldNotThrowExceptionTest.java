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
package org.apache.camel.jaxb;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.Bar;
import org.apache.camel.example.Foo;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class FallbackTypeConverterShouldNotThrowExceptionTest extends CamelTestSupport {

    @Test
    public void testJaxbModel() throws InterruptedException {
        Object foo = new Foo();
        getMockEndpoint("mock:a").expectedBodiesReceived(foo);
        getMockEndpoint("mock:b").expectedBodiesReceived(foo);

        template.sendBody("direct:a", foo);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoneJaxbModel() throws InterruptedException {
        Object camel = "Camel";
        getMockEndpoint("mock:a").expectedBodiesReceived(camel);
        getMockEndpoint("mock:b").expectedBodiesReceived(camel);

        template.sendBody("direct:a", camel);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAnotherJaxbModel() throws InterruptedException {
        Object bar = new Bar();
        getMockEndpoint("mock:a").expectedBodiesReceived(bar);
        getMockEndpoint("mock:b").expectedBodiesReceived(bar);

        template.sendBody("direct:a", bar);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder(context) {

            @Override
            public void configure() throws Exception {
                from("direct:a").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should return null and not throw any exception if the conversion fails
                        Foo foo = exchange.getIn().getBody(Foo.class);
                        if (!(exchange.getIn().getBody() instanceof Foo)) {
                            assertNull("Failed conversion didn't return null", foo);
                        }
                    }

                }).to("mock:a").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // should return null and not throw any exception if the conversion fails
                        List<?> list = exchange.getIn().getBody(List.class);
                        assertNull("Failed conversion didn't return null", list);
                    }

                }).to("mock:b");
            }

        };
    }

}