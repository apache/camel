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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 *
 */
public class FileMulticastDeleteTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/inbox");
        super.setUp();
    }

    public void testFileMulticastDelete() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Got Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/inbox", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/inbox?delete=true&initialDelay=0&delay=10")
                    .multicast(new UseLatestAggregationStrategy()).shareUnitOfWork()
                        .to("direct:foo", "direct:bar")
                    .end()
                    .convertBodyTo(String.class)
                    .to("mock:result");

                from("direct:foo")
                    .to("log:foo")
                    .aggregate(header(Exchange.FILE_NAME), new MyFileAggregator()).completionTimeout(100)
                        .convertBodyTo(String.class)
                        .to("mock:foo")
                    .end();

                from("direct:bar")
                    .to("log:bar")
                    .convertBodyTo(String.class)
                    .to("mock:bar");
            }
        };
    }

    public class MyFileAggregator implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // load data
            String data = newExchange.getIn().getBody(String.class);
            newExchange.getIn().setBody("Got " + data);
            return newExchange;
        }
    }
}
