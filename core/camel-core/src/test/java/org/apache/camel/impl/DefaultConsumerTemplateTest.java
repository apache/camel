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

package org.apache.camel.impl;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.engine.DefaultConsumerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultConsumerTemplateTest extends ContextTestSupport {
    private static final String TEST_FILE_NAME = "hello" + UUID.randomUUID() + ".txt";
    private static final String TEST_SEDA_CONSUMER = "foo" + UUID.randomUUID();

    private DefaultConsumerTemplate consumer;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        consumer = new DefaultConsumerTemplate(context);
        consumer.start();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        consumer.stop();
        super.tearDown();
    }

    @Test
    public void testConsumeReceive() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        Exchange out = consumer.receive("seda:" + TEST_SEDA_CONSUMER);
        assertNotNull(out);
        assertEquals("Hello", out.getIn().getBody());

        assertSame(context, consumer.getCamelContext());
    }

    @Test
    public void testConsumeTwiceReceive() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        Exchange out = consumer.receive("seda:" + TEST_SEDA_CONSUMER);
        assertNotNull(out);
        assertEquals("Hello", out.getIn().getBody());

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Bye");

        out = consumer.receive("seda:" + TEST_SEDA_CONSUMER);
        assertNotNull(out);
        assertEquals("Bye", out.getIn().getBody());
    }

    @Test
    public void testConsumeReceiveNoWait() {
        Exchange out = consumer.receiveNoWait("seda:" + TEST_SEDA_CONSUMER);
        assertNull(out);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).until(() -> {
            Exchange foo = consumer.receiveNoWait("seda:" + TEST_SEDA_CONSUMER);
            if (foo != null) {
                assertEquals("Hello", foo.getIn().getBody());
            }
            return foo != null;
        });
    }

    @Test
    public void testConsumeReceiveTimeout() {
        StopWatch watch = new StopWatch();
        Exchange out = consumer.receive("seda:" + TEST_SEDA_CONSUMER, 1000);
        assertNull(out);
        long delta = watch.taken();
        assertTrue(delta < 1500, "Should take about 1 sec: " + delta);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        out = consumer.receive("seda:" + TEST_SEDA_CONSUMER);
        assertEquals("Hello", out.getIn().getBody());
    }

    @Test
    public void testConsumeReceiveBody() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        Object body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeTwiceReceiveBody() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        Object body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER);
        assertEquals("Hello", body);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Bye");

        body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER);
        assertEquals("Bye", body);
    }

    @Test
    public void testConsumeReceiveBodyNoWait() {
        Object body = consumer.receiveBodyNoWait("seda:" + TEST_SEDA_CONSUMER);
        assertNull(body);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Object foo = consumer.receiveBodyNoWait("seda:" + TEST_SEDA_CONSUMER);
            assertEquals("Hello", foo);
        });
    }

    @Test
    public void testConsumeReceiveBodyString() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        String body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, String.class);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeTwiceReceiveBodyString() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        String body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, String.class);
        assertEquals("Hello", body);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Bye");

        body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, String.class);
        assertEquals("Bye", body);
    }

    @Test
    public void testConsumeReceiveBodyStringNoWait() {
        String body = consumer.receiveBodyNoWait("seda:" + TEST_SEDA_CONSUMER, String.class);
        assertNull(body);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            String foo = consumer.receiveBodyNoWait("seda:" + TEST_SEDA_CONSUMER, String.class);
            assertEquals("Hello", foo);
        });
    }

    @Test
    public void testConsumeReceiveEndpoint() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Exchange out = consumer.receive(endpoint);
        assertEquals("Hello", out.getIn().getBody());
    }

    @Test
    public void testConsumeReceiveEndpointTimeout() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Exchange out = consumer.receive(endpoint, 1000);
        assertEquals("Hello", out.getIn().getBody());
    }

    @Test
    public void testConsumeReceiveEndpointNoWait() {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Exchange out = consumer.receiveNoWait(endpoint);
        assertNull(out);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).until(() -> {
            Exchange foo = consumer.receiveNoWait(endpoint);
            if (foo != null) {
                assertEquals("Hello", foo.getIn().getBody());
            }
            return foo != null;
        });
    }

    @Test
    public void testConsumeReceiveEndpointBody() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Object body = consumer.receiveBody(endpoint);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeReceiveEndpointBodyTimeout() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Object body = consumer.receiveBody(endpoint, 1000);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeReceiveEndpointBodyType() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        String body = consumer.receiveBody(endpoint, String.class);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeReceiveEndpointBodyTimeoutType() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        String body = consumer.receiveBody(endpoint, 1000, String.class);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeReceiveBodyTimeoutType() {
        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        String body = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, 1000, String.class);
        assertEquals("Hello", body);
    }

    @Test
    public void testConsumeReceiveEndpointBodyTypeNoWait() {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        String out = consumer.receiveBodyNoWait(endpoint, String.class);
        assertNull(out);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            String foo = consumer.receiveBodyNoWait(endpoint, String.class);
            assertEquals("Hello", foo);
        });
    }

    @Test
    public void testConsumeReceiveEndpointBodyNoWait() {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:" + TEST_SEDA_CONSUMER);

        Object out = consumer.receiveBodyNoWait(endpoint);
        assertNull(out);

        template.sendBody("seda:" + TEST_SEDA_CONSUMER, "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Object foo = consumer.receiveBodyNoWait(endpoint);
            assertEquals("Hello", foo);
        });
    }

    @Test
    public void testReceiveException() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setException(new IllegalArgumentException("Damn"));

        Exchange out = template.send("seda:" + TEST_SEDA_CONSUMER, exchange);
        assertTrue(out.isFailed());
        assertNotNull(out.getException());

        RuntimeCamelException e = assertThrows(
                RuntimeCamelException.class,
                () -> consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, String.class),
                "Should have thrown an exception");

        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Damn", e.getCause().getMessage());
    }

    @Test
    public void testReceiveOut() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("Bye World");
        template.send("seda:" + TEST_SEDA_CONSUMER, exchange);

        String out = consumer.receiveBody("seda:" + TEST_SEDA_CONSUMER, String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testCacheConsumers() {
        ConsumerTemplate template = new DefaultConsumerTemplate(context);
        template.setMaximumCacheSize(500);
        template.start();

        assertEquals(0, template.getCurrentCacheSize(), "Size should be 0");

        // test that we cache at most 500 consumers to avoid it eating to much
        // memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            template.receiveNoWait(e);
        }

        await().atMost(3, TimeUnit.SECONDS).until(() -> {
            // the eviction is async so force cleanup
            template.cleanUp();
            return template.getCurrentCacheSize() == 500;
        });
        assertEquals(500, template.getCurrentCacheSize(), "Size should be 500");
        template.stop();

        // should be 0
        assertEquals(0, template.getCurrentCacheSize(), "Size should be 0");
    }

    @Test
    public void testCacheConsumersFromContext() {
        ConsumerTemplate template = context.createConsumerTemplate(500);

        assertEquals(0, template.getCurrentCacheSize(), "Size should be 0");

        // test that we cache at most 500 consumers to avoid it eating to much
        // memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            template.receiveNoWait(e);
        }

        await().atMost(3, TimeUnit.SECONDS).until(() -> {
            // the eviction is async so force cleanup
            template.cleanUp();
            return template.getCurrentCacheSize() == 500;
        });
        assertEquals(500, template.getCurrentCacheSize(), "Size should be 500");
        template.stop();

        // should be 0
        assertEquals(0, template.getCurrentCacheSize(), "Size should be 0");
    }

    @Test
    public void testDoneUoW() {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, TEST_FILE_NAME);

        Exchange exchange = consumer.receive(fileUri("?initialDelay=0&delay=10&delete=true"));
        assertNotNull(exchange);
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // file should still exists
        assertFileExists(testFile(TEST_FILE_NAME));

        // done the exchange
        consumer.doneUoW(exchange);

        assertFileNotExists(testFile(TEST_FILE_NAME));
    }
}
