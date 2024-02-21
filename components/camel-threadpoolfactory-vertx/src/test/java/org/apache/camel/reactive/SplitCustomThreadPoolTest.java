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
package org.apache.camel.reactive;

import io.vertx.core.Vertx;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.reactive.vertx.VertXThreadPoolFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SplitCustomThreadPoolTest extends CamelTestSupport {

    private final Vertx vertx = Vertx.vertx();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        VertXThreadPoolFactory tpf = (VertXThreadPoolFactory) context.getExecutorServiceManager().getThreadPoolFactory();
        tpf.setVertx(vertx);

        return context;
    }

    @Test
    public void testSplit() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C,D,E,F,G,H,I,J");
        getMockEndpoint("mock:split").expectedBodiesReceivedInAnyOrder("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");

        template.sendBody("direct:start", "A,B,C,D,E,F,G,H,I,J");

        MockEndpoint.assertIsSatisfied(context);

        vertx.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // register a custom thread pool profile with id myLowPool
                context.getExecutorServiceManager().registerThreadPoolProfile(
                        new ThreadPoolProfileBuilder("myLowPool").poolSize(2).maxPoolSize(10).build());

                from("direct:start")
                        .to("log:foo")
                        .split(body()).executorService("myLowPool")
                        .to("log:bar")
                        .process(e -> {
                            String name = Thread.currentThread().getName();
                            assertTrue(name.startsWith("Camel"), "Should use Camel thread");
                        })
                        .to("mock:split")
                        .end()
                        .to("log:result")
                        .to("mock:result");
            }
        };
    }
}
