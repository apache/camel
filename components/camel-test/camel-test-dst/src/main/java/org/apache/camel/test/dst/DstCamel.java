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
package org.apache.camel.test.dst;

import java.util.function.Consumer;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.SimpleUuidGenerator;

/**
 * Camel helpers for a DST run.
 */
public final class DstCamel {

    private DstCamel() {
    }

    /**
     * A CamelContext whose thread pools run on the loop of the run, with deterministic exchange ids and no JMX. Start
     * it on the loop thread.
     */
    public static DefaultCamelContext newContext(Dst dst) {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getCamelContextExtension().setName("dst");
        context.disableJMX();
        context.setUuidGenerator(new SimpleUuidGenerator());
        context.getExecutorServiceManager().setThreadPoolFactory(new DstThreadPoolFactory(dst));
        context.getShutdownStrategy().setTimeout(5);
        return context;
    }

    /**
     * An asynchronous step of a route, for example a call to a non-blocking downstream system: the exchange continues
     * after a latency chosen by the seed, on the given executor. {@code body} runs when the call answers and may set an
     * exception on the exchange (a failure of the downstream system).
     */
    public static AsyncProcessor asyncStep(
            Dst dst, DstExecutor executor, long minLatency, long maxLatency, Consumer<Exchange> body) {
        return new AsyncProcessorSupport() {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                long latency = dst.between(minLatency, maxLatency);
                executor.schedule("answer of " + executor.label(), latency, () -> {
                    try {
                        body.accept(exchange);
                    } catch (RuntimeException e) {
                        exchange.setException(e);
                    }
                    callback.done(false);
                });
                return false;
            }
        };
    }
}
