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
package org.apache.camel.language.simple;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-24885: a resource: text with a function but no scheme is a plain expression (it used to recurse into a
 * StackOverflowError); with a scheme it is the dynamic resource it always was.
 */
public class SimpleResourceWithoutSchemeTest extends ContextTestSupport {

    @Test
    public void testWithoutASchemeItIsPlainText() throws Exception {
        getMockEndpoint("mock:plain").expectedBodiesReceived("resource:templates/order.json");
        template.sendBodyAndHeader("direct:plain", "x", "type", "order");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithASchemeTheResourceIsLoaded() throws Exception {
        getMockEndpoint("mock:dynamic").expectedBodiesReceived("The name is x");
        template.sendBodyAndHeader("direct:dynamic", "x", "name", "mysimple");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:plain").setBody(simple("resource:templates/${header.type}.json")).to("mock:plain");
                from("direct:dynamic").setBody(simple("resource:classpath:${header.name}.txt")).to("mock:dynamic");
            }
        };
    }
}
