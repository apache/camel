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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25792: poll and pollEnrich evaluate a fileName without the file functions on the message being enriched, so
 * ${body} is its body and not the file found. A fileName with the file functions is still evaluated for each file
 * found.
 */
class PollDynamicFileNameBodyTest extends ContextTestSupport {

    @Test
    void testFileNameFromBody() throws Exception {
        getMockEndpoint("mock:body").expectedMessageCount(2);
        getMockEndpoint("mock:body").message(0).body().isEqualTo("Hello World");
        getMockEndpoint("mock:body").message(1).body().isNull();

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");
        template.sendBody("direct:body", "myfile.txt");
        template.sendBody("direct:body", "unknown.txt");

        assertMockEndpointsSatisfied();
        long c = context.getEndpoints().stream()
                .filter(e -> e.getEndpointKey().startsWith("file") && e.getEndpointUri().contains("?fileName=")).count();
        assertThat(c).as("Optimized: should reuse a single file endpoint").isEqualTo(1);
    }

    @Test
    void testFileNameFromAKeyOfTheBody() throws Exception {
        getMockEndpoint("mock:map").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "C-482.json");
        template.sendBody("direct:map", Map.of("orderId", "ORD-1001", "customer", "C-482"));

        assertMockEndpointsSatisfied();
    }

    @Test
    void testFileNameFromVariable() throws Exception {
        getMockEndpoint("mock:variable").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");
        template.send("direct:variable", e -> {
            e.getMessage().setBody("Foo");
            e.setVariable("target", "myfile.txt");
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    void testFileFunctionsReadTheFileFound() throws Exception {
        getMockEndpoint("mock:file").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "data.csv");
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "myfile.txt");
        template.sendBody("direct:file", "Foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:body")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${body}")
                        .timeout(500)
                        .to("mock:body");

                from("direct:map")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${body[customer]}.json")
                        .timeout(500)
                        .to("mock:map");

                from("direct:variable")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${variable.target}")
                        .timeout(500)
                        .to("mock:variable");

                // the name of each file found with the .txt extension: only myfile.txt matches
                from("direct:file")
                        .pollEnrich().simple(fileUri() + "?noop=true&fileName=${file:onlyname.noext}.txt")
                        .timeout(500)
                        .to("mock:file");
            }
        };
    }
}
