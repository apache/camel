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
import org.apache.camel.util.StopWatch;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link ChunkComponent} and {@link ChunkEndpoint}
 */
public class ChunkComponentTest extends CamelTestSupport {

    @EndpointInject("mock:endSimple")
    protected MockEndpoint endSimpleMock;

    @Produce("direct:startSimple")
    protected ProducerTemplate startSimpleProducerTemplate;

    /**
     * Test without Resource URI header defined
     */
    @Test
    public void testChunk() throws Exception {
        // Prepare
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.expectedBodiesReceived("Earth to Andrew. Come in, Andrew.\n");
        // Act
        startSimpleProducerTemplate.sendBodyAndHeader("The Body", "name", "Andrew");
        // Verify
        assertMockEndpointsSatisfied();
    }

    /**
     * Test using Resource URI header
     */
    @Test
    public void testChunkWithResourceUriHeader() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader("name", "Andrew");
        exchange.getIn().setHeader(ChunkConstants.CHUNK_RESOURCE_URI, "hello");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.expectedBodiesReceived("Earth to Andrew. Come in, Andrew.\n");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        assertMockEndpointsSatisfied();
    }

    /**
     * Performance test
     */
    @Test
    public void testChunkPerformance() throws Exception {
        int messageCount = 10000;
        endSimpleMock.expectedMessageCount(messageCount);
        StopWatch stopwatch = new StopWatch(true);
        for (int i = 0; i < messageCount; i++) {
            startSimpleProducerTemplate.sendBodyAndHeader("The Body", "name", "Andrew");
        }
        assertMockEndpointsSatisfied();
        LoggerFactory.getLogger(getClass()).info("Chunk performance: " + stopwatch.taken() + "ms for " + messageCount + " messages");

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startSimple")
                        .to("chunk://file")
                        .to("mock:endSimple");
            }
        };
    }
}

