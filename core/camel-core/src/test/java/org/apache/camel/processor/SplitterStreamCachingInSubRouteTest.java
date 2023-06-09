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
package org.apache.camel.processor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.junit.jupiter.api.Test;

public class SplitterStreamCachingInSubRouteTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolThreshold(1L);

                from("direct:startIterable").split(body().tokenize(",")).streaming()
                        .aggregationStrategy(new InternalAggregationStrategy()).stopOnException().parallelProcessing()
                        .to("direct:sub").end().to("mock:result");

                from("direct:start").split(body().tokenize(",")).aggregationStrategy(new InternalAggregationStrategy())
                        .stopOnException().parallelProcessing().to("direct:sub")
                        .end().to("mock:result");

                from("direct:sub").process(new InputProcessorWithStreamCache(22)).to("mock:resultsub");

                from("direct:startNested").split(body().tokenize(",")).aggregationStrategy(new InternalAggregationStrategy())
                        .stopOnException().parallelProcessing()
                        .to("direct:start").end().to("mock:resultNested");
            }

        };
    }

    @Test
    public void testWithAggregationStategyAndStreamCacheInSubRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Test Message 22");
        template.sendBody("direct:start", "<start></start>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStreamCacheIterableSplitter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Test Message 22");
        template.sendBody("direct:startIterable", "<start></start>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNested() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultNested");
        mock.expectedBodiesReceived("Test Message 22");
        template.sendBody("direct:startNested", "<start></start>");

        assertMockEndpointsSatisfied();
    }

    public static class InputProcessorWithStreamCache implements Processor {

        private final int number;

        public InputProcessorWithStreamCache(int number) {
            this.number = number;
        }

        @Override
        public void process(Exchange exchange) throws Exception {

            CachedOutputStream cos = new CachedOutputStream(exchange);
            String s = "Test Message " + number;
            cos.write(s.getBytes(StandardCharsets.UTF_8));
            cos.close();
            InputStream is = (InputStream) cos.newStreamCache();

            exchange.getMessage().setBody(is);
        }
    }

    public static class InternalAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            try {
                String oldBody = oldExchange.getIn().getBody(String.class);
                String newBody = newExchange.getIn().getBody(String.class);
                String merged = oldBody + newBody;
                // also do stream caching in the aggregation strategy
                CachedOutputStream cos = new CachedOutputStream(newExchange);
                cos.write(merged.getBytes("UTF-8"));
                cos.close();
                oldExchange.getIn().setBody(cos.newStreamCache());
                return oldExchange;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
