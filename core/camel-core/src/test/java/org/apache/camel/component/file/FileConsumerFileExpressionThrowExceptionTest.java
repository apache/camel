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
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * Unit test for expression option for file consumer.
 */
public class FileConsumerFileExpressionThrowExceptionTest extends ContextTestSupport {

    private static volatile String event = "";
    private static volatile Exception rollbackCause;

    private static final CountDownLatch LATCH = new CountDownLatch(1);

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/filelanguage");
        super.setUp();
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("counter", new MyGuidGenerator());
        jndi.bind("myPoll", new MyPollStrategy());
        return jndi;
    }

    @Test
    public void testConsumeExpressionThrowException() throws Exception {
        template.sendBodyAndHeader("file://target/data/filelanguage/bean", "Bye World", Exchange.FILE_NAME, "123.txt");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/data/filelanguage/bean/" + "?pollStrategy=#myPoll&initialDelay=0&delay=10&fileName=${bean:counter?method=doNotExistMethod}.txt&delete=true")
                    .to("mock:result");
                // specify a method name that does not exists
            }
        });

        await().atMost(2, TimeUnit.SECONDS).until(() -> LATCH.getCount() == 0);

        // and we should rollback X number of times
        assertTrue(event.startsWith("rollback"));

        assertNotNull(rollbackCause);

        MethodNotFoundException e = assertIsInstanceOf(MethodNotFoundException.class, rollbackCause);
        assertNotNull(e);
        assertEquals("doNotExistMethod", e.getMethodName());
    }

    public class MyGuidGenerator {
        public String next() {
            return "123";
        }
    }

    private static class MyPollStrategy implements PollingConsumerPollStrategy {

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            event += "commit";
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
            event += "rollback";
            rollbackCause = cause;
            LATCH.countDown();
            return false;
        }
    }

}
