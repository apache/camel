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
package org.apache.camel.component.kamelet;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class KameletLocationTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("routes-builder-loader-xml", new MyRoutesLoader());
        return context;
    }

    @Test
    public void testOne() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HELLO");

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HELLO", "WORLD");

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "World");

        MockEndpoint.assertIsSatisfied(context);
    }

    // we cannot use camel-xml-io-dsl to load the XML route so we fool Camel
    // and use this class that has the route template hardcoded from java
    public class MyRoutesLoader implements RoutesBuilderLoader {

        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public String getSupportedExtension() {
            return "xml";
        }

        @Override
        public RoutesBuilder loadRoutesBuilder(Resource resource) {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    routeTemplate("upper")
                            .from("kamelet:source")
                            .transform().simple("${body.toUpperCase()}");
                }
            };
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void stop() {
            // noop
        }
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .kamelet("upper?location=file:src/test/resources/upper-kamelet.xml")
                        .to("mock:result");
            }
        };
    }
}
