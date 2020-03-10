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
package org.apache.camel.component.file;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * Unit test for poll strategy
 */
public class FileConsumerPollStrategyRollbackThrowExceptionTest extends ContextTestSupport {

    private static volatile String event = "";

    private static final CountDownLatch LATCH = new CountDownLatch(1);

    private String fileUrl = "file://target/data/pollstrategy/?pollStrategy=#myPoll&initialDelay=0&delay=10";

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myPoll", new MyPollStrategy());
        return jndi;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/pollstrategy");
        super.setUp();
        template.sendBodyAndHeader("file:target/data/pollstrategy/", "Hello World", Exchange.FILE_NAME, "hello.txt");
    }

    @Test
    public void testRollbackThrowException() throws Exception {
        await().atMost(2, TimeUnit.SECONDS).until(() -> LATCH.getCount() == 0);

        // and we should rollback X number of times
        assertTrue(event.startsWith("rollback"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    private static class MyPollStrategy implements PollingConsumerPollStrategy {

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            // simulate an error on first poll
            throw new IllegalArgumentException("Damn I cannot do this");
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            event += "commit";
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
            event += "rollback";
            LATCH.countDown();
            throw cause;
        }
    }

}
