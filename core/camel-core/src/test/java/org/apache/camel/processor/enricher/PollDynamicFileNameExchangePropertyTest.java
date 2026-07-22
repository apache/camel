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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PollDynamicFileNameExchangePropertyTest extends ContextTestSupport {

    @Test
    void testPollEnrichFileNameFromExchangeProperty() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:result").message(0).body().isEqualTo("Hello World");
        getMockEndpoint("mock:result").message(1).body().isNull();

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");

        template.sendBodyAndProperty("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndProperty("direct:start", "Bar", "target", "unknown.txt");

        assertMockEndpointsSatisfied();

        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("file") && e.getEndpointUri().contains("?fileName=")).count();
        assertThat(c).as("Optimized: should reuse a single file endpoint").isEqualTo(1);
    }

    @Test
    void testPollEnrichTwoFilesFromExchangeProperty() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "myfile2.txt");

        template.sendBodyAndProperty("direct:start", "Foo", "target", "myfile.txt");
        template.sendBodyAndProperty("direct:start", "Bar", "target", "myfile2.txt");

        assertMockEndpointsSatisfied();

        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("file") && e.getEndpointUri().contains("?fileName=")).count();
        assertThat(c).as("Optimized: should reuse a single file endpoint").isEqualTo(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${exchangeProperty.target}")
                        .timeout(500)
                        .to("mock:result");
            }
        };
    }
}
