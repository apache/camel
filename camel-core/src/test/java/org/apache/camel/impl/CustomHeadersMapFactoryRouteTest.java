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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.HeadersMapFactory;

public class CustomHeadersMapFactoryRouteTest extends ContextTestSupport {

    private HeadersMapFactory custom = new CustomHeadersMapFactory();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setHeadersMapFactory(custom);
        return context;
    }

    public void testCustomHeaders() throws Exception {
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);
        getMockEndpoint("mock:result").expectedHeaderReceived("FOO", 456);
        getMockEndpoint("mock:result").expectedHeaderReceived("Bar", "yes");

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", 123);
        headers.put("FOO", 456);
        headers.put("Bar", "yes");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();

        assertSame(custom, context.getHeadersMapFactory());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mock:result");
            }
        };
    }

    private static class CustomHeadersMapFactory implements HeadersMapFactory {

        @Override
        public Map<String, Object> newMap() {
            return new HashMap<>();
        }

        @Override
        public Map<String, Object> newMap(Map<String, Object> map) {
            return new HashMap<>(map);
        }

        @Override
        public boolean isInstanceOf(Map<String, Object> map) {
            return map instanceof HashMap;
        }

        @Override
        public boolean isCaseInsensitive() {
            return false;
        }
    }
}
