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
package org.apache.camel.component.reactive.streams.platforms;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 *
 */
public abstract class AbstractPlatformTestSupport extends CamelTestSupport {

    @Test
    public void testPublisher() throws Exception {

        int num = 20;

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:endpoint")
                        .to("reactive-streams:integers");
            }
        }.addRoutesToCamelContext(context);

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        List<Integer> elements = new LinkedList<>();
        CountDownLatch latch = new CountDownLatch(num);

        this.changeSign(camel.fromStream("integers", Integer.class), i -> {
            elements.add(i);
            latch.countDown();
        });

        context.start();

        for (int i = 1; i <= num; i++) {
            template.sendBody("direct:endpoint", i);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        for (Integer number : elements) {
            assertTrue(number < 0);
        }

    }


    @Test
    public void testSubscriber() throws Exception {

        int num = 20;

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:integers")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        CamelReactiveStreamsService camel = CamelReactiveStreams.get(context);

        List<Integer> elements = new LinkedList<>();
        for (int i = 1; i <= num; i++) {
            elements.add(i);
        }

        changeSign(elements, camel.streamSubscriber("integers", Integer.class));
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:endpoint");
        mock.expectedMessageCount(num);
        mock.assertIsSatisfied();

        for (Exchange ex : mock.getExchanges()) {
            Integer number = ex.getIn().getBody(Integer.class);
            assertNotNull(number);
            assertTrue(number < 0);
        }

    }


    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract void changeSign(Publisher<Integer> data, Consumer<Integer> consume);

    protected abstract void changeSign(Iterable<Integer> data, Subscriber<Integer> camel);

}
