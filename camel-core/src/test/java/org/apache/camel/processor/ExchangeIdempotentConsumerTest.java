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
package org.apache.camel.processor;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.ExchangeIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;

/**
 * @version 
 */
public class ExchangeIdempotentConsumerTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    private MyIdempotentRepo repo = new MyIdempotentRepo();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        assertEquals(0, repo.getExchange().size());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(
                        header("messageId"), repo
                ).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();

        // we used 6 different exchanges
        assertEquals(6, repo.getExchange().size());

        for (Exchange exchange : resultEndpoint.getExchanges()) {
            // should be in repo list
            assertTrue("Should contain the exchange", repo.getExchange().contains(exchange.getExchangeId()));
        }
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
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    private final class MyIdempotentRepo implements ExchangeIdempotentRepository<String> {

        private IdempotentRepository<String> delegate;
        private Set<String> exchanges = new LinkedHashSet<String>();

        private MyIdempotentRepo() {
            delegate = MemoryIdempotentRepository.memoryIdempotentRepository(200);
        }

        @Override
        public boolean add(Exchange exchange, String key) {
            exchanges.add(exchange.getExchangeId());
            return delegate.add(key);
        }

        @Override
        public boolean contains(Exchange exchange, String key) {
            exchanges.add(exchange.getExchangeId());
            return delegate.contains(key);
        }

        @Override
        public boolean remove(Exchange exchange, String key) {
            exchanges.add(exchange.getExchangeId());
            return delegate.remove(key);
        }

        @Override
        public boolean confirm(Exchange exchange, String key) {
            exchanges.add(exchange.getExchangeId());
            return delegate.confirm(key);
        }
        
        @Override
        public void clear() {
            delegate.clear();           
        }

        @Override
        public boolean add(String key) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean contains(String key) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean remove(String key) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean confirm(String key) {
            throw new UnsupportedOperationException("Should not be called");
        }

        public Set<String> getExchange() {
            return exchanges;
        }

        @Override
        public void start() throws Exception {
            // noop
        }

        @Override
        public void stop() throws Exception {
            // noop
        }
    }

}
