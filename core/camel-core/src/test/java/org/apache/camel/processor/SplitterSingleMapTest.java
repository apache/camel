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
package org.apache.camel.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SplitterSingleMapTest extends ContextTestSupport {

    @Test
    public void testSplitSingleMap() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:line");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Map.class);

        Map<String, String> map = new LinkedHashMap<>();
        map.put("123", "Hello World");
        map.put("789", "Bye World");

        template.sendBody("direct:start", new Order(44, map));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .split(simple("${body.data}"), "single")
                        .to("mock:line")
                    .end();
            }
        };
    }

    private static class Order {

        private final int id;
        private final Map<String, String> data;

        public Order(int id, Map<String, String> data) {
            this.id = id;
            this.data = data;
        }

        public int getId() {
            return id;
        }

        public Map<String, String> getData() {
            return data;
        }
    }
}
