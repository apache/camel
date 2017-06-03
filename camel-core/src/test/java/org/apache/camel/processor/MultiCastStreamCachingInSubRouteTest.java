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
package org.apache.camel.processor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class MultiCastStreamCachingInSubRouteTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setSpoolDirectory("target/camel/cache");
                context.getStreamCachingStrategy().setSpoolThreshold(1L);

                from("direct:start").multicast(new InternalAggregationStrategy()).to("direct:a", "direct:b").end().to("mock:result");

                from("direct:startNestedMultiCast").multicast(new InternalAggregationStrategy()).to("direct:start").end()
                        .to("mock:resultNested");

                from("direct:a") //
                        .process(new InputProcessorWithStreamCache(1)) //
                        .to("mock:resulta");

                from("direct:b") //
                        .process(new InputProcessorWithStreamCache(2)) //
                        .to("mock:resultb");
            }
        };
    }

    public void testWithAggregationStrategyAndStreamCacheInSubRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Test Message 1Test Message 2");
        template.sendBody("direct:start", "<start></start>");

        assertMockEndpointsSatisfied();
    }

    public void testNestedMultiCastWithCachedStreamInAggregationStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultNested");
        mock.expectedBodiesReceived("Test Message 1Test Message 2");
        template.sendBody("direct:startNestedMultiCast", "<start></start>");

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
            cos.write(s.getBytes(Charset.forName("UTF-8")));
            cos.close();
            InputStream is = (InputStream) cos.newStreamCache();
            exchange.getOut().setBody(is);

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
                //also do stream caching in the aggregation strategy            
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
