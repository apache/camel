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
package org.apache.camel.example;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DataFormatConcurrentTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DataFormatConcurrentTest.class);

    private int size = 2000;
    private int warmupCount = 100;
    private int testCycleCount = 10000;
    private int fooBarSize = 50;

    @Test
    public void testUnmarshalConcurrent() {
        assertDoesNotThrow(() -> runUnmarshalConcurrent());
    }

    private void runUnmarshalConcurrent() throws Exception {
        template.setDefaultEndpointUri("direct:unmarshal");
        final CountDownLatch latch = new CountDownLatch(warmupCount + testCycleCount);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal")
                        .unmarshal(new JaxbDataFormat("org.apache.camel.example"))
                        .process(exchange -> latch.countDown());
            }
        });

        unmarshal(latch);
    }

    @Test
    public void testUnmarshalFallbackConcurrent() {
        assertDoesNotThrow(() -> runUnmarshallFallbackConcurrent());
    }

    private void runUnmarshallFallbackConcurrent() throws Exception {
        template.setDefaultEndpointUri("direct:unmarshalFallback");
        final CountDownLatch latch = new CountDownLatch(warmupCount + testCycleCount);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshalFallback")
                        .convertBodyTo(Foo.class)
                        .process(exchange -> latch.countDown());
            }
        });

        unmarshal(latch);
    }

    @Test
    public void testMarshallConcurrent() {
        assertDoesNotThrow(() -> runMarshallConcurrent());
    }

    private void runMarshallConcurrent() throws Exception {
        template.setDefaultEndpointUri("direct:marshal");
        final CountDownLatch latch = new CountDownLatch(warmupCount + testCycleCount);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal")
                        .marshal(new JaxbDataFormat("org.apache.camel.example"))
                        .process(exchange -> latch.countDown());
            }
        });

        marshal(latch);
    }

    @Test
    public void testMarshallFallbackConcurrent() {
        assertDoesNotThrow(() -> runMarshallFallbackConcurrent());
    }

    private void runMarshallFallbackConcurrent() throws Exception {
        template.setDefaultEndpointUri("direct:marshalFallback");
        final CountDownLatch latch = new CountDownLatch(warmupCount + testCycleCount);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshalFallback")
                        .convertBodyTo(String.class)
                        .process(exchange -> latch.countDown());
            }
        });

        marshal(latch);
    }

    @Test
    public void testSendConcurrent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(size);

        // wait for seda consumer to start up properly
        Thread.sleep(1000);

        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < size; i++) {

            // sleep a little so we interleave with the marshaller
            Thread.sleep(1, 500);

            executor.execute(new Runnable() {
                public void run() {
                    PurchaseOrder bean = new PurchaseOrder();
                    bean.setName("Beer");
                    bean.setAmount(23);
                    bean.setPrice(2.5);

                    template.sendBody("seda:start?size=" + size + "&concurrentConsumers=5", bean);
                }
            });
        }

        MockEndpoint.assertIsSatisfied(context);
    }

    public void unmarshal(final CountDownLatch latch) throws Exception {
        // warm up
        ByteArrayInputStream[] warmUpPayloads = createPayloads(warmupCount);
        for (ByteArrayInputStream payload : warmUpPayloads) {
            template.sendBody(payload);
        }

        final ByteArrayInputStream[] payloads = createPayloads(testCycleCount);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        StopWatch watch = new StopWatch();
        for (int i = 0; i < payloads.length; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                public void run() {
                    template.sendBody(payloads[finalI]);
                }
            });
        }

        latch.await();
        long duration = watch.taken();

        LOG.info("Sending {} messages to {} took {} ms",
                payloads.length, template.getDefaultEndpoint().getEndpointUri(), duration);
    }

    public void marshal(final CountDownLatch latch) throws Exception {
        // warm up
        Foo[] warmUpPayloads = createFoo(warmupCount);
        for (Foo payload : warmUpPayloads) {
            template.sendBody(payload);
        }

        final Foo[] payloads = createFoo(testCycleCount);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        StopWatch watch = new StopWatch();
        for (int i = 0; i < payloads.length; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                public void run() {
                    template.sendBody(payloads[finalI]);
                }
            });
        }

        latch.await();
        long duration = watch.taken();

        LOG.info("Sending {} messages to {} took {} ms",
                payloads.length, template.getDefaultEndpoint().getEndpointUri(), duration);
    }

    /**
     * the individual size of one record is: fooBarSize = 1 -> 104 bytes fooBarSize = 50 -> 2046 bytes
     *
     * @return the payloads used for this stress test
     */
    public Foo[] createFoo(int testCount) {
        Foo[] foos = new Foo[testCount];
        for (int i = 0; i < testCount; i++) {
            Foo foo = new Foo();
            for (int x = 0; x < fooBarSize; x++) {
                Bar bar = new Bar();
                bar.setName("Name: " + x);
                bar.setValue("value: " + x);
                foo.getBarRefs().add(bar);
            }

            foos[i] = foo;
        }

        return foos;
    }

    /**
     * the individual size of one record is: fooBarSize = 1 -> 104 bytes fooBarSize = 50 -> 2046 bytes
     *
     * @return           the payloads used for this stress test
     * @throws Exception
     */
    public ByteArrayInputStream[] createPayloads(int testCount) throws Exception {
        Foo foo = new Foo();
        for (int x = 0; x < fooBarSize; x++) {
            Bar bar = new Bar();
            bar.setName("Name: " + x);
            bar.setValue("value: " + x);
            foo.getBarRefs().add(bar);
        }
        Marshaller m = JAXBContext.newInstance(Foo.class, Bar.class).createMarshaller();
        StringWriter writer = new StringWriter();
        m.marshal(foo, writer);

        byte[] payload = writer.toString().getBytes();
        ByteArrayInputStream[] streams = new ByteArrayInputStream[testCount];
        for (int i = 0; i < testCount; i++) {
            streams[i] = new ByteArrayInputStream(payload);
        }

        return streams;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                DataFormat jaxb = new JaxbDataFormat("org.apache.camel.example");

                // use seda that supports concurrent consumers for concurrency
                from("seda:start?size=" + size + "&concurrentConsumers=5")
                        .marshal(jaxb)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
