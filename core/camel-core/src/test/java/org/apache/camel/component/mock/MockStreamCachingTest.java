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
package org.apache.camel.component.mock;

import java.io.ByteArrayInputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MockStreamCachingTest extends ContextTestSupport {

    @Test
    public void testMockStreamCaching() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body(String.class).contains("Camel");
        getMockEndpoint("mock:result").message(0).body(String.class).contains("World");

        Object body = new ByteArrayInputStream("Hello Camel and Bye World".getBytes());

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMockStreamCachingConvertTo() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().convertToString().contains("Camel");
        getMockEndpoint("mock:result").message(0).body().convertToString().contains("World");

        Object body = new ByteArrayInputStream("Hello Camel and Bye World".getBytes());

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").streamCaching().to("mock:result");
            }
        };
    }
}
