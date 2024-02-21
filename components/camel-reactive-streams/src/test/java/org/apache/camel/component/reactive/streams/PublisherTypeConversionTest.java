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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublisherTypeConversionTest extends BaseReactiveTest {

    @Test
    public void testConversion() throws Exception {

        CountDownLatch latch = new CountDownLatch(3);
        List<Integer> integers = new LinkedList<>();

        Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("pub", Exchange.class))
                .map(x -> x.getIn().getBody(Integer.class))
                .subscribe(n -> {
                    integers.add(n);
                    latch.countDown();
                });

        Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("pub"))
                .map(x -> x.getIn().getBody(Integer.class))
                .subscribe(n -> {
                    integers.add(n);
                    latch.countDown();
                });

        Observable.fromPublisher(CamelReactiveStreams.get(context).fromStream("pub", Integer.class))
                .subscribe(n -> {
                    integers.add(n);
                    latch.countDown();
                });

        context.start();
        latch.await(5, TimeUnit.SECONDS);

        assertEquals(3, integers.size());

        for (int i : integers) {
            assertEquals(123, i);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick?period=50&repeatCount=1")
                        .setBody().constant(123)
                        .to("reactive-streams:pub");
            }
        };
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }
}
