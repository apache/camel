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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Exchange did not complete.");
        assertNull(failed, "Should not have failed");
        assertNotNull(completed, "Should have received completed notification");
        assertEquals("bar", foo, "Should have propagated the header inside the Synchronization.onComplete() callback");
        assertNull(baz, "The Synchronization.onFailure() callback should have not been invoked");

        log.info("Received completed: {}", completed);
    }

    @Test
    public void testException() throws Exception {
        sendMessage();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Exchange did not complete.");
        assertNull(completed, "Should not have completed");
        assertNotNull(failed, "Should have received failed notification");
        assertEquals("bat", baz, "Should have propagated the header inside the Synchronization.onFailure() callback");
        assertNull(foo, "The Synchronization.onComplete() callback should have not been invoked");

        log.info("Received fail: {}", failed);
    }

    @Override
    @BeforeEach
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
                        log.info("Received: {}", exchange);
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
