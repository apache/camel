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
package org.apache.camel.component.mock;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MockExpectedBodiesAsListTest extends ContextTestSupport {

    public void testUsingVarargs() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testUsingListString() throws Exception {
        List<String> data = new ArrayList<String>();
        data.add("Hello World");
        data.add("Bye World");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(data);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testUsingList() throws Exception {
        List<Object> data = new ArrayList<Object>();
        data.add("Hello World");
        data.add(123);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(data);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }
}
