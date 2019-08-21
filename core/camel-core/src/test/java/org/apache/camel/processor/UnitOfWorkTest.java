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
package org.apache.camel.processor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Synchronization;
import org.junit.Before;
import org.junit.Test;

public class UnitOfWorkTest extends ContextTestSupport {
    protected Synchronization synchronization;
    protected Exchange completed;
    protected Exchange failed;
    protected String uri = "direct:foo";
    protected CountDownLatch doneLatch = new CountDownLatch(1);
    protected Object foo;
    protected Object baz;

    @Test
    public void testSuccess() throws Exception {
        sendMessage();

        assertTrue("Exchange did not complete.", doneLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have failed", failed);
        assertNotNull("Should have received completed notification", completed);
        assertEquals("Should have propagated the header inside the Synchronization.onComplete() callback", "bar", foo);
        assertNull("The Synchronization.onFailure() callback should have not been invoked", baz);

        log.info("Received completed: " + completed);
    }

    @Test
    public void testException() throws Exception {
        sendMessage();

        assertTrue("Exchange did not complete.", doneLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have completed", completed);
        assertNotNull("Should have received failed notification", failed);
        assertEquals("Should have propagated the header inside the Synchronization.onFailure() callback", "bat", baz);
        assertNull("The Synchronization.onComplete() callback should have not been invoked", foo);

        log.info("Received fail: " + failed);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        synchronization = new Synchronization() {
            public void onComplete(Exchange exchange) {
                completed = exchange;
                foo = exchange.getIn().getHeader("foo");
                doneLatch.countDown();
            }

            public void onFailure(Exchange exchange) {
                failed = exchange;
                baz = exchange.getIn().getHeader("baz");
                doneLatch.countDown();
            }
        };

        super.setUp();
    }

    protected void sendMessage() throws InterruptedException {

        template.send(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("foo", "bar");
                exchange.getIn().setHeader("baz", "bat");
                exchange.getIn().setBody("<hello>world!</hello>");
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:async").to("direct:foo");
                from("direct:foo").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        log.info("Received: " + exchange);
                        exchange.getUnitOfWork().addSynchronization(synchronization);

                        String name = getName();
                        if (name.equals("testException")) {
                            log.info("Throwing exception!");
                            throw new Exception("Failing test!");
                        }
                    }
                });
            }
        };
    }
}
