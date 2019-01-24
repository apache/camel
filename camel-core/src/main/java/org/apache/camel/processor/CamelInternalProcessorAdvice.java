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

import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.spi.ManagementInterceptStrategy.InstrumentationProcessor;

/**
 * An advice (before and after) to execute cross cutting functionality in the Camel routing engine.
 * <p/>
 * The Camel routing engine will execute the {@link #before(org.apache.camel.Exchange)} and
 * {@link #after(org.apache.camel.Exchange, Object)} methods during routing in correct order.
 *
 * @param <T>
 * @see org.apache.camel.processor.CamelInternalProcessor
 */
public interface CamelInternalProcessorAdvice<T> {

    /**
     * Callback executed before processing a step in the route.
     *
     * @param exchange  the current exchange
     * @return any state to keep and provide as data to the {@link #after(org.apache.camel.Exchange, Object)} method, or use <tt>null</tt> for no state.
     * @throws Exception is thrown if error during the call.
     */
    T before(Exchange exchange) throws Exception;

    /**
     * Callback executed after processing a step in the route.
     *
     * @param exchange  the current exchange
     * @param data      the state, if any, returned in the {@link #before(org.apache.camel.Exchange)} method.
     * @throws Exception is thrown if error during the call.
     */
    void after(Exchange exchange, T data) throws Exception;

    /**
     * Wrap an InstrumentationProcessor into a CamelInternalProcessorAdvice
     */
    static <T> CamelInternalProcessorAdvice<T> wrap(InstrumentationProcessor<T> instrumentationProcessor) {

        if (instrumentationProcessor instanceof CamelInternalProcessor) {
            return (CamelInternalProcessorAdvice<T>) instrumentationProcessor;
        } else {
            return new CamelInternalProcessorAdviceWrapper<T>(instrumentationProcessor);
        }
    }

    static Object unwrap(CamelInternalProcessorAdvice<?> advice) {
        if (advice instanceof CamelInternalProcessorAdviceWrapper) {
            return ((CamelInternalProcessorAdviceWrapper) advice).unwrap();
        } else {
            return advice;
        }
    }

    class CamelInternalProcessorAdviceWrapper<T> implements CamelInternalProcessorAdvice<T>, Ordered {

        final InstrumentationProcessor<T> instrumentationProcessor;

        public CamelInternalProcessorAdviceWrapper(InstrumentationProcessor<T> instrumentationProcessor) {
            this.instrumentationProcessor = instrumentationProcessor;
        }

        InstrumentationProcessor<T> unwrap() {
            return instrumentationProcessor;
        }

        @Override
        public int getOrder() {
            return instrumentationProcessor.getOrder();
        }

        @Override
        public T before(Exchange exchange) throws Exception {
            return instrumentationProcessor.before(exchange);
        }

        @Override
        public void after(Exchange exchange, T data) throws Exception {
            instrumentationProcessor.after(exchange, data);
        }
    }

}
