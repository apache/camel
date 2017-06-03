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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Subscriber;


public class EventTypeTest extends CamelTestSupport {

    @Test
    public void testOnCompleteHeaderForwarded() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:numbers?forwardOnComplete=true")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        Subscriber<Integer> numbers = CamelReactiveStreams.get(context).streamSubscriber("numbers", Integer.class);

        context.start();

        Flowable.<Integer>empty()
                .subscribe(numbers);


        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(1);
        endpoint.expectedHeaderReceived(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onComplete");
        endpoint.expectedBodiesReceived(new Object[]{null});
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOnCompleteHeaderNotForwarded() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:numbers")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        Subscriber<Integer> numbers = CamelReactiveStreams.get(context).streamSubscriber("numbers", Integer.class);

        context.start();

        Flowable.<Integer>empty()
                .subscribe(numbers);


        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(0);
        endpoint.assertIsSatisfied(200);
    }

    @Test
    public void testOnNextHeaderForwarded() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:numbers")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        Subscriber<Integer> numbers = CamelReactiveStreams.get(context).streamSubscriber("numbers", Integer.class);

        context.start();

        Flowable.just(1)
                .subscribe(numbers);

        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedHeaderReceived(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onNext");
        endpoint.expectedMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange ex = endpoint.getExchanges().get(0);
        assertEquals(1, ex.getIn().getBody());
    }

    @Test
    public void testOnErrorHeaderForwarded() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:numbers?forwardOnError=true")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        Subscriber<Integer> numbers = CamelReactiveStreams.get(context).streamSubscriber("numbers", Integer.class);

        context.start();

        RuntimeException ex = new RuntimeException("1");

        Flowable.just(1)
                .map(n -> {
                    if (n == 1) {
                        throw ex;
                    }
                    return n;
                })
                .subscribe(numbers);


        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(1);
        endpoint.expectedHeaderReceived(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onError");
        endpoint.assertIsSatisfied();

        Exchange exch = endpoint.getExchanges().get(0);
        assertEquals(ex, exch.getIn().getBody());
    }

    @Test
    public void testOnErrorHeaderNotForwarded() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:numbers")
                        .to("mock:endpoint");
            }
        }.addRoutesToCamelContext(context);

        Subscriber<Integer> numbers = CamelReactiveStreams.get(context).streamSubscriber("numbers", Integer.class);

        context.start();

        RuntimeException ex = new RuntimeException("1");

        Flowable.just(1)
                .map(n -> {
                    if (n == 1) {
                        throw ex;
                    }
                    return n;
                })
                .subscribe(numbers);


        MockEndpoint endpoint = getMockEndpoint("mock:endpoint");
        endpoint.expectedMessageCount(0);
        endpoint.assertIsSatisfied(200);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
