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
package org.apache.camel.component.lumberjack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Isolated
public class LumberjackMultiThreadTest extends CamelTestSupport {

    private static int port;

    @BeforeAll
    public static void beforeClass() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Lumberjack configured with a specific port
                from("lumberjack:0.0.0.0:" + port).to("mock:output");
            }
        };
    }

    @Test
    public void shouldListenToMessages() throws Exception {
        int concurrencyLevel = Math.min(Runtime.getRuntime().availableProcessors(), 4);

        // We're expecting 25 messages with Maps for each thread
        MockEndpoint mock = getMockEndpoint("mock:output");
        mock.expectedMessageCount(25 * concurrencyLevel);
        mock.allMessages().body().isInstanceOf(Map.class);

        // When sending messages
        List<Integer> windows = Arrays.asList(15, 10);

        // create a number of threads
        List<LumberjackThreadTest> threads = new ArrayList<>();

        for (int i = 0; i < concurrencyLevel; i++) {
            threads.add(new LumberjackThreadTest());
        }

        // sending messages on all parallel sessions
        threads.stream().forEach(thread -> thread.start());

        // Then we should have the messages we're expecting
        mock.assertIsSatisfied();

        // And the first map should contains valid data (we're assuming it's also valid for the other ones)
        Map first = mock.getExchanges().get(0).getIn().getBody(Map.class);
        assertEquals("log", first.get("input_type"));
        assertEquals("/home/qatest/collectNetwork/log/data-integration/00000000-f000-0000-1541-8da26f200001/absorption.log",
                first.get("source"));

        Awaitility.await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> threads.stream().forEach(thread -> assertEquals(windows, thread.responses)));
    }

    class LumberjackThreadTest extends Thread {
        private List<Integer> responses;

        @Override
        public void run() {
            try {
                this.responses = LumberjackUtil.sendMessages(port, null, Arrays.asList(15, 10));
            } catch (InterruptedException e) {
                fail(e);
            }
        }

    }
}
