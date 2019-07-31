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
package org.apache.camel.component.chunk;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for {@link ChunkComponent} and {@link ChunkEndpoint}
 */
public class ChunkComponentTemplateTest extends CamelTestSupport {

    @EndpointInject("mock:endSimple")
    protected MockEndpoint endSimpleMock;

    @Produce("direct:startSimple")
    protected ProducerTemplate startSimpleProducerTemplate;
    
    /**
     * Test using code Template header
     */
    @Test
    public void testChunkWithTemplateHeader() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader("someHeader", "Some Header");
        exchange.getIn().setHeader(ChunkConstants.CHUNK_TEMPLATE, "Body='{$body}'|SomeHeader='{$headers.someHeader}'");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.expectedBodiesReceived("Body='The Body'|SomeHeader='Some Header'");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startSimple")
                        .to("chunk://hello")
                        .to("mock:endSimple");
            }
        };
    }
}
