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
package org.apache.camel.processor.groovy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class GroovySetHeaderConcurrentIssueTest extends CamelTestSupport {

    private ExecutorService executor;

    public static final class MySubOrder {

        private String name;

        public MySubOrder(String name) {
            this.name = name;
        }

        public String getSubOrderName() {
            return name;
        }
    }

    @Test
    public void testGroovySetHeader() throws Exception {
        getMockEndpoint("mock:0Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:1Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:2Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:3Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:4Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:5Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:6Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:7Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:8Endpoint").expectedMessageCount(100);
        getMockEndpoint("mock:9Endpoint").expectedMessageCount(100);

        executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < 1000; i++) {
            final Integer count = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    template.sendBody("direct:start", new MySubOrder("mock:" + count % 10));
                }
            });
        }

        assertMockEndpointsSatisfied();

        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setHeader("mySlip").groovy("return \"${request.body.subOrderName}Endpoint\"")
                    .routingSlip(header("mySlip"));
            }
        };
    }
}
