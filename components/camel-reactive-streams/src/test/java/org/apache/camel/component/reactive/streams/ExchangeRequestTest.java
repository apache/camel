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
package org.apache.camel.component.reactive.streams;

import io.reactivex.Flowable;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;


public class ExchangeRequestTest extends CamelTestSupport {

    @Test
    public void testStreamRequest() throws Exception {

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Publisher<Exchange> string = camel.toStream("data", new DefaultExchange(context));

        Exchange res = Flowable.fromPublisher(string).blockingFirst();

        assertNotNull(res);

        String content = res.getIn().getBody(String.class);
        assertNotNull(content);
        assertEquals("123", content);
    }

    @Test
    public void testInteraction() throws Exception {

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Integer res = Flowable.fromPublisher(camel.toStream("plusOne", 1L, Integer.class))
                .blockingFirst();

        assertNotNull(res);
        assertEquals(2, res.intValue());
    }

    @Test
    public void testMultipleInteractions() throws Exception {
        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Integer sum = Flowable.just(1, 2, 3)
                .flatMap(e -> camel.toStream("plusOne", e, Integer.class))
                .reduce((i, j) -> i + j)
                .blockingGet();

        assertNotNull(sum);
        assertEquals(9, sum.intValue());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:data")
                        .setBody().constant("123");

                from("reactive-streams:plusOne")
                        .setBody().body(Integer.class, b -> b + 1)
                        .log("Hello ${body}");
            }
        };
    }
}
