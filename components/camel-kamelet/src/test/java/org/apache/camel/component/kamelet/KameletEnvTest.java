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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KameletEnvTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("routes-builder-loader-xml", new MyRoutesLoader());
        return context;
    }

    @Test
    public void testCalc() throws Exception {
        Assertions.assertEquals("4", System.getenv().get("NUMBER"));

        // OS ENV is 4, but the kamelet should use the parameter as-is which is 3
        // and as such the expected result would be 2 x 3 = 6
        getMockEndpoint("mock:result").expectedBodiesReceived("6");
        template.sendBody("direct:start", "3");
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
                    routeTemplate("double")
                            .templateParameter("number")
                            .from("kamelet:source")
                            .transform().groovy("2 * {{number}}");
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
                        .kamelet("double?location=file:src/test/resources/double-kamelet.xml&number=3")
                        .to("mock:result");
            }
        };
    }
}
