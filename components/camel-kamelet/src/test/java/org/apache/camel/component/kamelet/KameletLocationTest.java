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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.http.annotation.Obsolete;
import org.junit.jupiter.api.Test;

public class KameletLocationTest extends CamelTestSupport {

    @Test
    public void testOne() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HELLO");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HELLO", "WORLD");

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Obsolete
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .kamelet("upper?location=file:src/test/resources/upper-kamelet.xml")
                        .to("mock:result");
            }
        };
    }
}
