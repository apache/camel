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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Wire tap unit test
 *
 * @version 
 */
public class WireTapBeanTest extends ContextTestSupport {
    protected MockEndpoint tap;
    protected MockEndpoint result;

    public void testSend() throws Exception {
        result.expectedBodiesReceived("Bye World");
        tap.expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tap = getMockEndpoint("mock:tap");
        result = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("log:foo")
                    .wireTap("seda:tap")
                    .bean(MyBean.class)
                    .to("mock:result");

                from("seda:tap")
                    .to("mock:tap");
            }
        };
    }

    public static class MyBean {

        public String transform(String body) {
            return "Bye " + body;
        }
    }
}