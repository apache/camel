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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.reactive.streams.support.ReactiveStreamsTestSupport;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;
import org.reactivestreams.Publisher;


public class DirectClientAPITest extends ReactiveStreamsTestSupport {

    @Test
    public void testFromDirect() throws Exception {

        Publisher<Integer> data = camel.from("direct:endpoint", Integer.class);

        BlockingQueue<Integer> queue = new LinkedBlockingDeque<>();

        Flowable.fromPublisher(data)
                .map(i -> -i)
                .doOnNext(queue::add)
                .subscribe();

        context.start();
        template.sendBody("direct:endpoint", 1);

        Integer res = queue.poll(1, TimeUnit.SECONDS);
        assertNotNull(res);
        assertEquals(-1, res.intValue());
    }

    @Test
    public void testFromDirectOnHotContext() throws Exception {

        context.start();
        Thread.sleep(200);

        Publisher<Integer> data = camel.from("direct:endpoint", Integer.class);

        BlockingQueue<Integer> queue = new LinkedBlockingDeque<>();

        Flowable.fromPublisher(data)
                .map(i -> -i)
                .doOnNext(queue::add)
                .subscribe();

        template.sendBody("direct:endpoint", 1);

        Integer res = queue.poll(1, TimeUnit.SECONDS);
        assertNotNull(res);
        assertEquals(-1, res.intValue());
    }

    @Test
    public void testDirectCall() throws Exception {
        context.start();

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        Flowable.just(1, 2, 3)
                .flatMap(camel.to("bean:hello", String.class)::apply)
                .doOnNext(queue::add)
                .subscribe();

        for (int i = 1; i <= 3; i++) {
            String res = queue.poll(1, TimeUnit.SECONDS);
            assertEquals("Hello " + i, res);
        }

    }

    @Test
    public void testDirectSendAndForget() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:data")
                        .to("mock:result");
            }
        }.addRoutesToCamelContext(context);

        context.start();

        Flowable.just(1, 2, 3)
                .subscribe(camel.subscriber("direct:data", Integer.class));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied();

        int idx = 1;
        for (Exchange ex : mock.getExchanges()) {
            Integer num = ex.getIn().getBody(Integer.class);
            assertEquals(new Integer(idx++), num);
        }

    }

    @Test
    public void testDirectCallOverload() throws Exception {
        context.start();

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        Flowable.just(1, 2, 3)
                .flatMap(e -> camel.to("bean:hello", e, String.class))
                .doOnNext(queue::add)
                .subscribe();

        for (int i = 1; i <= 3; i++) {
            String res = queue.poll(1, TimeUnit.SECONDS);
            assertEquals("Hello " + i, res);
        }

    }

    @Test
    public void testDirectCallWithExchange() throws Exception {
        context.start();

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        Flowable.just(1, 2, 3)
                .flatMap(camel.to("bean:hello")::apply)
                .map(ex -> ex.getOut().getBody(String.class))
                .doOnNext(queue::add)
                .subscribe();

        for (int i = 1; i <= 3; i++) {
            String res = queue.poll(1, TimeUnit.SECONDS);
            assertEquals("Hello " + i, res);
        }

    }

    @Test
    public void testDirectCallWithExchangeOverload() throws Exception {
        context.start();

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        Flowable.just(1, 2, 3)
                .flatMap(e -> camel.to("bean:hello", e))
                .map(ex -> ex.getOut().getBody(String.class))
                .doOnNext(queue::add)
                .subscribe();

        for (int i = 1; i <= 3; i++) {
            String res = queue.poll(1, TimeUnit.SECONDS);
            assertEquals("Hello " + i, res);
        }

    }



    @Test
    public void testProxiedDirectCall() throws Exception {
        context.start();

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:proxy")
                        .to("bean:hello")
                        .setBody().simple("proxy to ${body}");
            }
        }.addRoutesToCamelContext(context);

        BlockingQueue<String> queue = new LinkedBlockingDeque<>();

        Flowable.just(1, 2, 3)
                .flatMap(camel.to("direct:proxy", String.class)::apply)
                .doOnNext(queue::add)
                .subscribe();

        for (int i = 1; i <= 3; i++) {
            String res = queue.poll(1, TimeUnit.SECONDS);
            assertEquals("proxy to Hello " + i, res);
        }

    }

    @Test
    public void testDirectCallFromCamel() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source")
                        .to("direct:stream")
                        .setBody().simple("after stream: ${body}")
                        .to("mock:dest");
            }
        }.addRoutesToCamelContext(context);

        context.start();

        camel.process("direct:stream", p ->
                Flowable.fromPublisher(p)
                        .map(exchange -> {
                            int val = exchange.getIn().getBody(Integer.class);
                            exchange.getOut().setBody(-val);
                            return exchange;
                        })
        );

        for (int i = 1; i <= 3; i++) {
            template.sendBody("direct:source", i);
        }

        MockEndpoint mock = getMockEndpoint("mock:dest");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied();

        int id = 1;
        for (Exchange ex : mock.getExchanges()) {
            String content = ex.getIn().getBody(String.class);
            assertEquals("after stream: " + (-id++), content);
        }
    }


    @Test
    public void testDirectCallFromCamelWithConversion() throws Exception {

        new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source")
                        .to("direct:stream")
                        .setBody().simple("after stream: ${body}")
                        .to("mock:dest");
            }
        }.addRoutesToCamelContext(context);

        context.start();

        camel.process("direct:stream", Integer.class, p ->
                Flowable.fromPublisher(p)
                        .map(i -> -i)
        );

        for (int i = 1; i <= 3; i++) {
            template.sendBody("direct:source", i);
        }

        MockEndpoint mock = getMockEndpoint("mock:dest");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied();

        int id = 1;
        for (Exchange ex : mock.getExchanges()) {
            String content = ex.getIn().getBody(String.class);
            assertEquals("after stream: " + (-id++), content);
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("hello", new SampleBean());
        return registry;
    }

    public static class SampleBean {

        public String hello(String name) {
            return "Hello " + name;
        }

    }

}
