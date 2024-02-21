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
package org.apache.camel.component.reactive.streams;

import io.reactivex.Flowable;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExchangeRequestTest extends BaseReactiveTest {

    @Test
    public void testStreamRequest() {

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Publisher<Exchange> string = camel.toStream("data", new DefaultExchange(context));

        Exchange res = Flowable.fromPublisher(string).blockingFirst();

        assertNotNull(res);

        String content = res.getIn().getBody(String.class);
        assertNotNull(content);
        assertEquals("123", content);
    }

    @Test
    public void testInteraction() {

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Integer res = Flowable.fromPublisher(camel.toStream("plusOne", 1L, Integer.class))
                .blockingFirst();

        assertNotNull(res);
        assertEquals(2, res.intValue());
    }

    @Test
    public void testMultipleInteractions() {
        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        Integer sum = Flowable.just(1, 2, 3)
                .flatMap(e -> camel.toStream("plusOne", e, Integer.class))
                .reduce((i, j) -> i + j)
                .blockingGet();

        assertNotNull(sum);
        assertEquals(9, sum.intValue());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("reactive-streams:data")
                        .setBody().constant("123");

                from("reactive-streams:plusOne")
                        .setBody().body(Integer.class, b -> b + 1)
                        .log("Hello ${body}");
            }
        };
    }
}
