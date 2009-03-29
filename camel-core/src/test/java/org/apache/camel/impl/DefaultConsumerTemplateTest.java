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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;

/**
 * @version $Revision$
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

        // a little delay to let the consumer see it
        Thread.sleep(10);

        out = consumer.receiveNoWait("seda:foo");
        assertEquals("Hello", out.getIn().getBody());
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

        // a little delay to let the consumer see it
        Thread.sleep(10);

        body = consumer.receiveBodyNoWait("seda:foo");
        assertEquals("Hello", body);
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

        // a little delay to let the consumer see it
        Thread.sleep(10);

        body = consumer.receiveBodyNoWait("seda:foo", String.class);
        assertEquals("Hello", body);
    }

}
