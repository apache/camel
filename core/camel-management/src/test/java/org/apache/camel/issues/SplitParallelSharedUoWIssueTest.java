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
package org.apache.camel.issues;

import java.util.stream.Stream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.management.ManagementTestSupport;
import org.junit.jupiter.api.RepeatedTest;

public class SplitParallelSharedUoWIssueTest extends ManagementTestSupport {

    @RepeatedTest(1000)
    public void testSplitParallelShareeUoW() throws Exception {
        getMockEndpoint("mock:line").expectedMessageCount(1000);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setBody(constant(Stream.iterate(1, i -> i + 1).limit(1000).toList()))
                    .split().body().shareUnitOfWork().parallelProcessing()
                        .log("Number ${body}")
                        .to("mock:line")
                    .end()
                    .log("All done")
                    .to("mock:result");
            }
        };
    }
}
