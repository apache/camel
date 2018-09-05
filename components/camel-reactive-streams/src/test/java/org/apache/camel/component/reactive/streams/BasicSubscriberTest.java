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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

public class BasicSubscriberTest extends CamelTestSupport {

    @Test
    public void testWorking() throws Exception {
        int count = 2;
        MockEndpoint e1 = getMockEndpoint("mock:sub1");
        e1.expectedMinimumMessageCount(count);
        e1.assertIsSatisfied();

        MockEndpoint e2 = getMockEndpoint("mock:sub2");
        e2.expectedMinimumMessageCount(count);
        e2.assertIsSatisfied();

        MockEndpoint e3 = getMockEndpoint("mock:sub3");
        e3.expectedMinimumMessageCount(count);
        e3.assertIsSatisfied();

        for (int i = 0; i < count; i++) {
            Exchange ex1 = e1.getExchanges().get(i);
            Exchange ex2 = e2.getExchanges().get(i);
            Exchange ex3 = e3.getExchanges().get(i);
            assertEquals(ex1.getIn().getBody(), ex2.getIn().getBody());
            assertEquals(ex1.getIn().getBody(), ex3.getIn().getBody());
        }
    }

    @Override
    protected void doPostSetup() throws Exception {

        Subscriber<Integer> sub = CamelReactiveStreams.get(context()).streamSubscriber("sub", Integer.class);
        Subscriber<Integer> sub2 = CamelReactiveStreams.get(context()).streamSubscriber("sub2", Integer.class);
        Publisher<Integer> pub = CamelReactiveStreams.get(context()).fromStream("pub", Integer.class);

        pub.subscribe(sub);
        pub.subscribe(sub2);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:sub")
                        .to("mock:sub1");

                from("reactive-streams:sub2")
                        .to("mock:sub2");

                from("timer:tick?period=50")
                        .setBody().simple("random(500)")
                        .wireTap("mock:sub3")
                        .to("reactive-streams:pub");
            }
        };
    }
}
