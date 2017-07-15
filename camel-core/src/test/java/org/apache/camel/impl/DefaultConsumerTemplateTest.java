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
package org.apache.camel.impl;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;

import static org.awaitility.Awaitility.await;

/**
 * @version 
 */
public class DefaultConsumerTemplateTest extends ContextTestSupport {

    private DefaultConsumerTemplate consumer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        consumer = new DefaultConsumerTemplate(context);
        consumer.start();
    }

    @Override
    protected void tearDown() throws Exception {
        consumer.stop();
        super.tearDown();
    }

    public void testConsumeReceive() throws Exception {
        template.sendBody("seda:foo", "Hello");

        Exchange out = consumer.receive("seda:foo");
        assertNotNull(out);
        assertEquals("Hello", out.getIn().getBody());

        assertSame(context, consumer.getCamelContext());
    }

    public void testConsumeTwiceReceive() throws Exception {
        template.sendBody("seda:foo", "Hello");

        Exchange out = consumer.receive("seda:foo");
        assertNotNull(out);
        assertEquals("Hello", out.getIn().getBody());

        template.sendBody("seda:foo", "Bye");

        out = consumer.receive("seda:foo");
        assertNotNull(out);
        assertEquals("Bye", out.getIn().getBody());
    }

    public void testConsumeReceiveNoWait() throws Exception {
        Exchange out = consumer.receiveNoWait("seda:foo");
        assertNull(out);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).until(() -> {
            Exchange foo = consumer.receiveNoWait("seda:foo");
            if (foo != null) {
                assertEquals("Hello", foo.getIn().getBody());
            }
            return foo != null;
        });
    }

    public void testConsumeReceiveTimeout() throws Exception {
        long start = System.currentTimeMillis();
        Exchange out = consumer.receive("seda:foo", 1000);
        assertNull(out);
        long delta = System.currentTimeMillis() - start;
        assertTrue("Should take about 1 sec: " + delta, delta < 1500);

        template.sendBody("seda:foo", "Hello");

        out = consumer.receive("seda:foo");
        assertEquals("Hello", out.getIn().getBody());
    }

    public void testConsumeReceiveBody() throws Exception {
        template.sendBody("seda:foo", "Hello");

        Object body = consumer.receiveBody("seda:foo");
        assertEquals("Hello", body);
    }

    public void testConsumeTwiceReceiveBody() throws Exception {
        template.sendBody("seda:foo", "Hello");

        Object body = consumer.receiveBody("seda:foo");
        assertEquals("Hello", body);

        template.sendBody("seda:foo", "Bye");

        body = consumer.receiveBody("seda:foo");
        assertEquals("Bye", body);
    }

    public void testConsumeReceiveBodyNoWait() throws Exception {
        Object body = consumer.receiveBodyNoWait("seda:foo");
        assertNull(body);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Object foo = consumer.receiveBodyNoWait("seda:foo");
            assertEquals("Hello", foo);
        });
    }

    public void testConsumeReceiveBodyString() throws Exception {
        template.sendBody("seda:foo", "Hello");

        String body = consumer.receiveBody("seda:foo", String.class);
        assertEquals("Hello", body);
    }

    public void testConsumeTwiceReceiveBodyString() throws Exception {
        template.sendBody("seda:foo", "Hello");

        String body = consumer.receiveBody("seda:foo", String.class);
        assertEquals("Hello", body);

        template.sendBody("seda:foo", "Bye");

        body = consumer.receiveBody("seda:foo", String.class);
        assertEquals("Bye", body);
    }

    public void testConsumeReceiveBodyStringNoWait() throws Exception {
        String body = consumer.receiveBodyNoWait("seda:foo", String.class);
        assertNull(body);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            String foo = consumer.receiveBodyNoWait("seda:foo", String.class);
            assertEquals("Hello", foo);
        });
    }

    public void testConsumeReceiveEndpoint() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Exchange out = consumer.receive(endpoint);
        assertEquals("Hello", out.getIn().getBody());
    }

    public void testConsumeReceiveEndpointTimeout() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Exchange out = consumer.receive(endpoint, 1000);
        assertEquals("Hello", out.getIn().getBody());
    }

    public void testConsumeReceiveEndpointNoWait() throws Exception {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Exchange out = consumer.receiveNoWait(endpoint);
        assertNull(out);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).until(() -> {
            Exchange foo = consumer.receiveNoWait(endpoint);
            if (foo != null) {
                assertEquals("Hello", foo.getIn().getBody());
            }
            return foo != null;
        });
    }

    public void testConsumeReceiveEndpointBody() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Object body = consumer.receiveBody(endpoint);
        assertEquals("Hello", body);
    }

    public void testConsumeReceiveEndpointBodyTimeout() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Object body = consumer.receiveBody(endpoint, 1000);
        assertEquals("Hello", body);
    }

    public void testConsumeReceiveEndpointBodyType() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        String body = consumer.receiveBody(endpoint, String.class);
        assertEquals("Hello", body);
    }

    public void testConsumeReceiveEndpointBodyTimeoutType() throws Exception {
        template.sendBody("seda:foo", "Hello");

        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        String body = consumer.receiveBody(endpoint, 1000, String.class);
        assertEquals("Hello", body);
    }

    public void testConsumeReceiveBodyTimeoutType() throws Exception {
        template.sendBody("seda:foo", "Hello");

        String body = consumer.receiveBody("seda:foo", 1000, String.class);
        assertEquals("Hello", body);
    }

    public void testConsumeReceiveEndpointBodyTypeNoWait() throws Exception {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        String out = consumer.receiveBodyNoWait(endpoint, String.class);
        assertNull(out);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            String foo = consumer.receiveBodyNoWait(endpoint, String.class);
            assertEquals("Hello", foo);
        });
    }

    public void testConsumeReceiveEndpointBodyNoWait() throws Exception {
        assertNotNull(consumer.getCamelContext());
        Endpoint endpoint = context.getEndpoint("seda:foo");

        Object out = consumer.receiveBodyNoWait(endpoint);
        assertNull(out);

        template.sendBody("seda:foo", "Hello");

        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Object foo = consumer.receiveBodyNoWait(endpoint);
            assertEquals("Hello", foo);
        });
    }

    public void testReceiveException() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setException(new IllegalArgumentException("Damn"));

        Exchange out = template.send("seda:foo", exchange);
        assertTrue(out.isFailed());
        assertNotNull(out.getException());

        try {
            consumer.receiveBody("seda:foo", String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Damn", e.getCause().getMessage());
        }
    }

    public void testReceiveOut() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getOut().setBody("Bye World");
        template.send("seda:foo", exchange);

        String out = consumer.receiveBody("seda:foo", String.class);
        assertEquals("Bye World", out);
    }

    public void testCacheConsumers() throws Exception {
        ConsumerTemplate template = new DefaultConsumerTemplate(context);
        template.setMaximumCacheSize(500);
        template.start();

        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache at most 500 consumers to avoid it eating to much memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            template.receiveNoWait(e);
        }

        // the eviction is async so force cleanup
        template.cleanUp();

        assertEquals("Size should be 500", 500, template.getCurrentCacheSize());
        template.stop();

        // should be 0
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }

    public void testCacheConsumersFromContext() throws Exception {
        ConsumerTemplate template = context.createConsumerTemplate(500);

        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());

        // test that we cache at most 500 consumers to avoid it eating to much memory
        for (int i = 0; i < 503; i++) {
            Endpoint e = context.getEndpoint("direct:queue:" + i);
            template.receiveNoWait(e);
        }

        // the eviction is async so force cleanup
        template.cleanUp();

        assertEquals("Size should be 500", 500, template.getCurrentCacheSize());
        template.stop();

        // should be 0
        assertEquals("Size should be 0", 0, template.getCurrentCacheSize());
    }

    public void testDoneUoW() throws Exception {
        deleteDirectory("target/foo");
        template.sendBodyAndHeader("file:target/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");

        Exchange exchange = consumer.receive("file:target/foo?initialDelay=0&delay=10&delete=true");
        assertNotNull(exchange);
        assertEquals("Hello World", exchange.getIn().getBody(String.class));

        // file should still exists
        File file = new File("target/foo/hello.txt");
        assertTrue("File should exist " + file, file.exists());

        // done the exchange
        consumer.doneUoW(exchange);

        assertFalse("File should have been deleted " + file, file.exists());
    }

}
