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
package org.apache.camel.component.chaos;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * A scheduled poll component for the tests ({@code testpoll:name}): it counts the polls that run, and a poll that finds
 * {@code messages} left sends one message into the route.
 */
final class TestPollComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        TestPollEndpoint endpoint = new TestPollEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    static final class TestPollEndpoint extends ScheduledPollEndpoint {

        TestPollEndpoint(String uri, TestPollComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    // nothing to send to
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            TestPollConsumer answer = new TestPollConsumer(this, processor);
            configureConsumer(answer);
            return answer;
        }
    }

    static final class TestPollConsumer extends ScheduledPollConsumer {

        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger messages = new AtomicInteger();

        TestPollConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected int poll() throws Exception {
            polls.incrementAndGet();
            if (messages.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                Exchange exchange = createExchange(false);
                exchange.getMessage().setBody("polled");
                try {
                    getProcessor().process(exchange);
                } finally {
                    releaseExchange(exchange, false);
                }
                return 1;
            }
            return 0;
        }
    }
}
