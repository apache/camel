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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.junit.Before;
import org.junit.Test;

public class IdempotentConsumerUsingCustomRepositoryTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    protected IdempotentRepository customRepo = new MyRepo();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(header("messageId"), customRepo).to("mock:result");
            }
        };
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        // 4 is already pre added in custom repo so it will be regarded as
        // duplicate
        sendMessage("4", "four");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // and the custom repo now contains those keys
        assertTrue(customRepo.contains("1"));
        assertTrue(customRepo.contains("2"));
        assertTrue(customRepo.contains("3"));
        assertTrue(customRepo.contains("4"));
        assertFalse(customRepo.contains("5"));

        customRepo.clear();

        assertFalse(customRepo.contains("1"));
        assertFalse(customRepo.contains("2"));
        assertFalse(customRepo.contains("3"));
        assertFalse(customRepo.contains("4"));
        assertFalse(customRepo.contains("5"));
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    private static final class MyRepo implements IdempotentRepository {
        private final Map<String, String> cache = new HashMap<>();

        private MyRepo() {
            // pre start with 4 already in there
            cache.put("4", "4");
        }

        @Override
        public boolean add(String key) {
            if (cache.containsKey(key)) {
                return false;
            } else {
                cache.put(key, key);
                return true;
            }
        }

        @Override
        public void clear() {
            cache.clear();
        }

        @Override
        public boolean contains(String key) {
            return cache.containsKey(key);
        }

        @Override
        public boolean remove(String key) {
            return cache.remove(key) != null;
        }

        @Override
        public boolean confirm(String key) {
            // noop
            return true;
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void stop() {
            // noop
        }
    }

}
