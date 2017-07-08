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
package org.apache.camel.component.file;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;

/**
 *
 */
public class FileConsumerSharedThreadPollTest extends ContextTestSupport {

    private ScheduledExecutorService pool;
    private SimpleRegistry registry = new SimpleRegistry();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/a");
        deleteDirectory("target/b");
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext(registry);
    }

    public void testSharedThreadPool() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        // thread thread name should be the same
        mock.message(0).header("threadName").isEqualTo(mock.message(1).header("threadName"));

        template.sendBodyAndHeader("file:target/a", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/b", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // create shared pool and enlist in registry
                pool = new ThreadPoolBuilder(context).poolSize(1).buildScheduled(this, "MySharedPool");
                registry.put("myPool", pool);

                from("file:target/a?initialDelay=0&delay=10&scheduledExecutorService=#myPool").routeId("a")
                    .to("direct:shared");

                from("file:target/b?initialDelay=0&delay=10&scheduledExecutorService=#myPool").routeId("b")
                    .to("direct:shared");

                from("direct:shared").routeId("shared")
                    .convertBodyTo(String.class)
                    .log("Get ${file:name} using ${threadName}")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader("threadName", Thread.currentThread().getName());
                        }
                    })
                    .to("mock:result");
            }
        };
    }
}
