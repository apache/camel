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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PollDynamicFileNameOptimizeDisabledTest extends ContextTestSupport {

    @Test
    public void testPollEnrichFileOne() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo("Hello World");
        getMockEndpoint("mock:result").message(1).body().isNull();

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");

        template.sendBodyAndHeader("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndHeader("direct:start", "Bar", "target", "unknown.txt");

        assertMockEndpointsSatisfied();

        // there should only be 1 file endpoint
        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("file") && e.getEndpointUri().contains("?fileName=")).count();
        Assertions.assertEquals(2, c, "There should only be 2 file endpoints");
    }

    @Test
    public void testPollEnrichFileTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "myfile2.txt");

        template.sendBodyAndHeader("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndHeader("direct:start", "Bar", "target", "myfile2.txt");

        assertMockEndpointsSatisfied();

        // there should only be 1 file endpoint
        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("file") && e.getEndpointUri().contains("?fileName=")).count();
        Assertions.assertEquals(2, c, "There should only be 2 file endpoints");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${header.target}")
                        .allowOptimisedComponents(false).timeout(500)
                        .to("mock:result");
            }
        };
    }
}
