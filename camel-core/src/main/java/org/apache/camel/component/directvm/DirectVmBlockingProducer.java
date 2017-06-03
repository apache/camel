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
package org.apache.camel.component.directvm;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The direct producer.
 * <p/>
 * If blocking is enabled ({@code DirectEndpoint#isBlock}) then the DirectEndpoint will create an instance
 * of this class instead of {@code DirectProducer}.
 * This producers {@code process} method will block for the configured duration ({@code DirectEndpoint#getTimeout},
 * default to 30 seconds). After which if a consumer is still unavailable a DirectConsumerNotAvailableException
 * will be thrown.
 * <p/>
 * Implementation note: Concurrent Producers will block for the duration it takes to determine if a
 * consumer is available, but actual consumer execution will happen concurrently.
 */
public class DirectVmBlockingProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(DirectVmBlockingProducer.class);
    private final DirectVmEndpoint endpoint;

    public DirectVmBlockingProducer(DirectVmEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        getConsumer(exchange).getProcessor().process(exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return getConsumer(exchange).getAsyncProcessor().process(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected DirectVmConsumer getConsumer(Exchange exchange) throws Exception {
        DirectVmConsumer answer = endpoint.getConsumer();
        if (answer == null) {
            // okay then await until we have a consumer or we timed out
            answer = awaitConsumer();
            if (answer == null) {
                throw new DirectVmConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange);
            }
        }

        return answer;
    }

    private DirectVmConsumer awaitConsumer() throws InterruptedException {
        DirectVmConsumer answer = null;

        StopWatch watch = new StopWatch();
        boolean done = false;
        while (!done) {
            // sleep a bit to give chance for the consumer to be ready
            Thread.sleep(500);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Waited {} for consumer to be ready", watch.taken());
            }

            answer = endpoint.getConsumer();
            if (answer != null) {
                return answer;
            }
            // we are done if we hit the timeout
            done = watch.taken() >= endpoint.getTimeout();
        }
        return answer;
    }

}
