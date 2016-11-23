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
package org.apache.camel.component.lumberjack;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

public class LumberjackDisconnectionTest extends CamelTestSupport {
    private static int port;

    @BeforeClass
    public static void beforeClass() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Lumberjack configured with something that throws an exception
                from("lumberjack:0.0.0.0:" + port).process(new ErrorProcessor()).to("mock:output");
            }
        };
    }

    @Test
    public void shouldDisconnectUponError() throws Exception {
        // We're expecting 3 messages with Maps
        // The fourth one crashed and we didn't received the next ones
        MockEndpoint mock = getMockEndpoint("mock:output");
        mock.expectedMessageCount(3);
        mock.allMessages().body().isInstanceOf(Map.class);

        // When sending messages
        List<Integer> responses = LumberjackUtil.sendMessages(port, null);

        // Then we should have the messages we're expecting
        mock.assertIsSatisfied();

        // And no acknowledgment is received
        assertCollectionSize(responses, 0);
    }

    /**
     * This processor throws an exception as the fourth message received.
     */
    private static final class ErrorProcessor implements Processor {
        int count;

        @Override
        public void process(Exchange exchange) throws Exception {
            count++;
            if (count == 4) {
                throw new RuntimeException("Ooops");
            }
        }
    }
}
