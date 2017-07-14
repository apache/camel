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
package org.apache.camel.component.file;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.PollingConsumerPollStrategy;

/**
 * Unit test for poll strategy
 */
public class FileConsumerPollStrategyPolledMessagesTest extends ContextTestSupport {

    private static int maxPolls;
    private final CountDownLatch latch = new CountDownLatch(1);

    private String fileUrl = "file://target/pollstrategy/?consumer.pollStrategy=#myPoll&initialDelay=0&delay=10";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myPoll", new MyPollStrategy());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/pollstrategy");
        super.setUp();
    }

    public void testPolledMessages() throws Exception {
        template.sendBodyAndHeader("file:target/pollstrategy/", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/pollstrategy/", "Bye World", Exchange.FILE_NAME, "bye.txt");

        // start route now files have been created
        context.startRoute("foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        assertMockEndpointsSatisfied();

        // wait for commit to be issued
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(2, maxPolls);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).routeId("foo").noAutoStartup()
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    private class MyPollStrategy implements PollingConsumerPollStrategy {

        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            if (polledMessages > maxPolls) {
                maxPolls = polledMessages;
            }
            latch.countDown();
        }

        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
            return false;
        }
    }

}