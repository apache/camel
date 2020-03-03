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

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for poll strategy
 */
public class FileConsumerPollStrategyStopOnRollbackTest extends ContextTestSupport {

    private static int counter;
    private static volatile String event = "";

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
    public void testStopOnRollback() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        // let it run for a little while and since it fails first time we should
        // never get a message
        mock.assertIsSatisfied(50);

        assertEquals("rollback", event);
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
            // start consumer as we simulate the fail in begin
            // and thus before camel lazy start it itself
            try {
                consumer.start();
            } catch (Exception e) {
                RuntimeCamelException.wrapRuntimeCamelException(e);
            }

            if (counter++ == 0) {
                // simulate an error on first poll
                throw new IllegalArgumentException("Damn I cannot do this");
            }

            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            event += "commit";
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception {
            if (cause.getMessage().equals("Damn I cannot do this")) {
                event += "rollback";
                // stop consumer as it does not work
                consumer.stop();
            }
            return false;
        }
    }

}
