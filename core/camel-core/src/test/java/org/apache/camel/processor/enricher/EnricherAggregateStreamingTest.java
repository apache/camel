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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.jupiter.api.Test;

/**
 * The original message body is a StreamCache. Therefore the stream cache must be reset before the aggregator is called.
 */
public class EnricherAggregateStreamingTest extends ContextTestSupport {

    @Test
    public void testStream() throws Exception {

        getMockEndpoint("mock:result").expectedBodiesReceived("Old Body New Body");

        template.sendBody("direct:start", "");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStreamSync() throws Exception {

        getMockEndpoint("mock:result").expectedBodiesReceived("Old Body New Body");

        template.sendBody("direct:startSync", "");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("b1", new MyProcessor()); // for synchronous call
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().setStreamCaching(Boolean.TRUE);
                StreamCachingStrategy scs = getContext().getStreamCachingStrategy();
                scs.setSpoolThreshold(1L);
                scs.setSpoolDirectory(testDirectory().toFile());
                from("direct:start").process(new StreamProcessor()).enrich("direct:foo", new MyAggregationStrategy(), false)
                        .to("mock:result");

                from("direct:foo").bean(new MyProcessor());

                from("direct:startSync").process(new StreamProcessor()).enrich().simple("bean:b1")
                        .aggregationStrategy(new MyAggregationStrategy()).to("mock:result");

            }
        };
    }

    public static class StreamProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            // consume stream by reading the body as string
            try (CachedOutputStream os = new CachedOutputStream(exchange)) {

                os.write("Old Body ".getBytes(StandardCharsets.UTF_8));

                InputStream is = os.getInputStream();
                exchange.getIn().setBody(is);
            }
        }
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            // consume stream by reading the body as string
            exchange.getIn().getBody(String.class);
            exchange.getIn().setBody("New Body");
        }
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            // consume stream by reading the body as string
            String oldbody = oldExchange.getIn().getBody(String.class);

            String newbody = newExchange.getIn().getBody(String.class);

            // replace body
            oldExchange.getIn().setBody(oldbody + newbody);
            return oldExchange;
        }
    }

}
