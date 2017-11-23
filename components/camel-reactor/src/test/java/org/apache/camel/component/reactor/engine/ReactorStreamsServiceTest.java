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
package org.apache.camel.component.reactor.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.ExchangeHelper;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class ReactorStreamsServiceTest extends ReactorStreamsServiceTestSupport {

    // ************************************************
    // Setup
    // ************************************************

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

    // ************************************************
    // fromStream/from
    // ************************************************

    @Test
    public void testFromStreamDirect() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:reactive")
                    .to("reactive-streams:numbers");
            }
        });

        context.start();
        ProducerTemplate template = context.createProducerTemplate();

        AtomicInteger value = new AtomicInteger(0);

        Flux.from(crs.fromStream("numbers", Integer.class))
            .doOnNext(res -> Assert.assertEquals(value.incrementAndGet(), res.intValue()))
            .subscribe();

        template.sendBody("direct:reactive", 1);
        template.sendBody("direct:reactive", 2);
        template.sendBody("direct:reactive", 3);
    }

    @Test
    public void testFromStreamTimer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:tick?period=5&repeatCount=30")
                    .setBody()
                        .header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:tick");
            }
        });

        final int num = 30;
        final CountDownLatch latch = new CountDownLatch(num);
        final AtomicInteger value = new AtomicInteger(0);

        Flux.from(crs.fromStream("tick", Integer.class))
            .doOnNext(res -> Assert.assertEquals(value.incrementAndGet(), res.intValue()))
            .doOnNext(n -> latch.countDown())
            .subscribe();

        context.start();

        latch.await(5, TimeUnit.SECONDS);

        Assert.assertEquals(num, value.get());
    }

    @Test
    public void testFromStreamMultipleSubscriptionsWithDirect() throws Exception {
        context.start();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:reactive")
                    .to("reactive-streams:direct");
            }
        });

        CountDownLatch latch1 = new CountDownLatch(2);
        Flux.from(crs.fromStream("direct", Integer.class))
            .doOnNext(res -> latch1.countDown())
            .subscribe();

        CountDownLatch latch2 = new CountDownLatch(2);
        Flux.from(crs.fromStream("direct", Integer.class))
            .doOnNext(res -> latch2.countDown())
            .subscribe();

        template.sendBody("direct:reactive", 1);
        template.sendBody("direct:reactive", 2);

        Assert.assertTrue(latch1.await(5, TimeUnit.SECONDS));
        Assert.assertTrue(latch2.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testMultipleSubscriptionsWithTimer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:tick?period=50")
                    .setBody().header(Exchange.TIMER_COUNTER)
                    .to("reactive-streams:tick");
            }
        });

        CountDownLatch latch1 = new CountDownLatch(5);
        Disposable disp1 = Flux.from(crs.fromStream("tick", Integer.class)).subscribe(res -> latch1.countDown());

        context.start();

        // Add another subscription
        CountDownLatch latch2 = new CountDownLatch(5);
        Disposable disp2 = Flux.from(crs.fromStream("tick", Integer.class)).subscribe(res -> latch2.countDown());

        assertTrue(latch1.await(5, TimeUnit.SECONDS));
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        // Un subscribe both
        disp1.dispose();
        disp2.dispose();

        // No active subscriptions, warnings expected
        Thread.sleep(60);

        // Add another subscription
        CountDownLatch latch3 = new CountDownLatch(5);
        Disposable disp3 = Flux.from(crs.fromStream("tick", Integer.class)).subscribe(res -> latch3.countDown());

        assertTrue(latch3.await(5, TimeUnit.SECONDS));
        disp3.dispose();
    }

    @Test
    public void testFrom() throws Exception {
        context.start();

        Publisher<Exchange> timer = crs.from("timer:reactive?period=250&repeatCount=3");

        AtomicInteger value = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        Flux.from(timer)
            .map(exchange -> ExchangeHelper.getHeaderOrProperty(exchange, Exchange.TIMER_COUNTER, Integer.class))
            .doOnNext(res -> Assert.assertEquals(value.incrementAndGet(), res.intValue()))
            .doOnNext(res -> latch.countDown())
            .subscribe();

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    // ************************************************
    // fromPublisher
    // ************************************************

    @Test
    public void testFromPublisher() throws Exception {
        context.start();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source")
                    .to("direct:stream")
                    .setBody()
                        .simple("after stream: ${body}");
            }
        });

        crs.process("direct:stream",
            publisher ->
                Flux.from(publisher)
                    .map(e -> {
                        int i = e.getIn().getBody(Integer.class);
                        e.getOut().setBody(-i);

                        return e;
                    }
                )
        );

        for (int i = 1; i <= 3; i++) {
            Assert.assertEquals(
                "after stream: " + (-i),
                template.requestBody("direct:source", i, String.class)
            );
        }
    }

    @Test
    public void testFromPublisherWithConversion() throws Exception {
        context.start();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:source")
                    .to("direct:stream")
                    .setBody()
                        .simple("after stream: ${body}");
            }
        });

        crs.process("direct:stream",
            Integer.class,
            publisher ->
                Flux.from(publisher).map(Math::negateExact)
        );

        for (int i = 1; i <= 3; i++) {
            Assert.assertEquals(
                "after stream: " + (-i),
                template.requestBody("direct:source", i, String.class)
            );
        }
    }

    // ************************************************
    // toStream/to
    // ************************************************

    @Test
    public void testToStream() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("reactive-streams:reactive")
                    .setBody().constant("123");
            }
        });

        context.start();

        Publisher<Exchange> publisher = crs.toStream("reactive", new DefaultExchange(context));
        Exchange res = Flux.from(publisher).blockFirst();

        Assert.assertNotNull(res);

        String content = res.getIn().getBody(String.class);

        Assert.assertNotNull(content);
        Assert.assertEquals("123", content);
    }

    @Test
    public void testTo() throws Exception {
        context.start();

        Set<String> values = Collections.synchronizedSet(new TreeSet<>());
        CountDownLatch latch = new CountDownLatch(3);

        Flux.just(1, 2, 3)
            .flatMap(e -> crs.to("bean:hello", e, String.class))
            .doOnNext(res -> values.add(res))
            .doOnNext(res -> latch.countDown())
            .subscribe();

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(new TreeSet<>(Arrays.asList("Hello 1", "Hello 2", "Hello 3")), values);
    }

    @Test
    public void testToWithExchange() throws Exception {
        context.start();

        Set<String> values = Collections.synchronizedSet(new TreeSet<>());
        CountDownLatch latch = new CountDownLatch(3);

        Flux.just(1, 2, 3)
            .flatMap(e -> crs.to("bean:hello", e))
            .map(e -> e.getOut())
            .map(e -> e.getBody(String.class))
            .doOnNext(res -> values.add(res))
            .doOnNext(res -> latch.countDown())
            .subscribe();

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(new TreeSet<>(Arrays.asList("Hello 1", "Hello 2", "Hello 3")), values);
    }

    @Test
    public void testToFunction() throws Exception {
        context.start();

        Set<String> values = Collections.synchronizedSet(new TreeSet<>());
        CountDownLatch latch = new CountDownLatch(3);
        Function<Object, Publisher<String>> fun = crs.to("bean:hello", String.class);

        Flux.just(1, 2, 3)
            .flatMap(fun)
            .doOnNext(res -> values.add(res))
            .doOnNext(res -> latch.countDown())
            .subscribe();

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(new TreeSet<>(Arrays.asList("Hello 1", "Hello 2", "Hello 3")), values);
    }

    @Test
    public void testToFunctionWithExchange() throws Exception {
        context.start();

        Set<String> values = Collections.synchronizedSet(new TreeSet<>());
        CountDownLatch latch = new CountDownLatch(3);
        Function<Object, Publisher<Exchange>> fun = crs.to("bean:hello");

        Flux.just(1, 2, 3)
            .flatMap(fun)
            .map(e -> e.getOut())
            .map(e -> e.getBody(String.class))
            .doOnNext(res -> values.add(res))
            .doOnNext(res -> latch.countDown())
            .subscribe();

        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        Assert.assertEquals(new TreeSet<>(Arrays.asList("Hello 1", "Hello 2", "Hello 3")), values);
    }

    // ************************************************
    // subscriber
    // ************************************************

    @Test
    public void testSubscriber() throws Exception {
        context.start();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:reactor")
                    .to("mock:result");
            }
        });

        Flux.just(1, 2, 3)
            .subscribe(crs.subscriber("direct:reactor", Integer.class));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.assertIsSatisfied();

        int idx = 1;
        for (Exchange ex : mock.getExchanges()) {
            Assert.assertEquals(new Integer(idx++), ex.getIn().getBody(Integer.class));
        }
    }

    // ************************************************
    // misc
    // ************************************************

    @Test(expected = IllegalStateException.class)
    public void testOnlyOneCamelProducerPerPublisher() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:one")
                    .to("reactive-streams:stream");
                from("direct:two")
                    .to("reactive-streams:stream");
            }
        });

        context.start();
    }
}
