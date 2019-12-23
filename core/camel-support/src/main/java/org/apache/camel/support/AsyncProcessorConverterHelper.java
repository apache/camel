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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.support.service.ServiceHelper;

/**
 * A simple converter that can convert any {@link Processor} to an {@link AsyncProcessor}.
 * Processing will still occur synchronously but it will provide the required
 * notifications that the caller expects.
 */
public final class AsyncProcessorConverterHelper {
    
    private AsyncProcessorConverterHelper() {
        // Helper class
    }

    /**
     * Creates a {@link AsyncProcessor} that delegates to the given processor.
     * It is important that this implements {@link DelegateProcessor}
     */
    // CHECKSTYLE:OFF
    private static class ProcessorToAsyncProcessorBridge implements DelegateProcessor, AsyncProcessor, Navigate<Processor>, Service {
    // CHECKSTYLE:ON
        protected final Processor processor;

        private ProcessorToAsyncProcessorBridge(Processor processor) {
            this.processor = processor;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            if (processor == null) {
                // no processor then we are done
                callback.done(true);
                return true;
            }
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                // must catch throwable so we catch all
                exchange.setException(e);
            } finally {
                // we are bridging a sync processor as async so callback with true
                callback.done(true);
            }
            return true;
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
            process(exchange, callback);
            return callback.getFuture();
        }

        @Override
        public String toString() {
            if (processor != null) {
                return processor.toString();
            } else {
                return "Processor is null";
            }
        }
        
        @Override
        public void process(Exchange exchange) throws Exception {
            processNext(exchange);
        }

        protected void processNext(Exchange exchange) throws Exception {
            if (processor != null) {
                processor.process(exchange);
            }
        }

        @Override
        public void start() {
            ServiceHelper.startService(processor);
        }

        @Override
        public void stop() {
            ServiceHelper.stopService(processor);
        }

        @Override
        public boolean hasNext() {
            return processor != null;
        }

        @Override
        public List<Processor> next() {
            if (!hasNext()) {
                return null;
            }
            List<Processor> answer = new ArrayList<>(1);
            answer.add(processor);
            return answer;
        }

        @Override
        public Processor getProcessor() {
            return processor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            if (processor == null) {
                return false;
            }

            ProcessorToAsyncProcessorBridge that = (ProcessorToAsyncProcessorBridge) o;
            return processor.equals(that.processor);
        }

        @Override
        public int hashCode() {
            if (processor != null) {
                return processor.hashCode();
            } else {
                return 0;
            }
        }
    }

    private static class ProducerToAsyncProducerBridge extends ProcessorToAsyncProcessorBridge implements AsyncProducer {
        ProducerToAsyncProducerBridge(Producer producer) {
            super(producer);
        }

        protected Producer producer() {
            return (Producer) processor;
        }

        @Override
        public Endpoint getEndpoint() {
            return producer().getEndpoint();
        }

        @Override
        public boolean isSingleton() {
            return producer().isSingleton();
        }
    }

    public static AsyncProducer convert(Producer value) {
        if (value instanceof AsyncProducer) {
            return (AsyncProducer) value;
        }
        return new ProducerToAsyncProducerBridge(value);
    }

    public static AsyncProcessor convert(Processor value) {
        if (value instanceof AsyncProcessor) {
            return (AsyncProcessor) value;
        }
        return new ProcessorToAsyncProcessorBridge(value);
    }
}
