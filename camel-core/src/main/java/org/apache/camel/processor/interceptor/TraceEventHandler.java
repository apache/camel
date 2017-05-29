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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

/**
 * A handler which reacts on trace events.
 */
@Deprecated
public interface TraceEventHandler {

    /**
     * Event called when an {@link Exchange} is about to be processed
     * <p/>
     * This event is only called if trace out has been disabled (which it is by default).
     * <p/>
     * This method is for coarse grained tracing, where as the the other two methods is for fine grained
     * with in and event events.
     *
     * @param node             the current node
     * @param target           the current processor being invoked
     * @param traceInterceptor the trace interceptor
     * @param exchange         the current exchange
     * @throws Exception is thrown if an error occurred during tracing
     */
    void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception;

    /**
     * Event called when an {@link Exchange} is about to be processed (in)
     * <p/>
     * This event is only called if trace out has been enabled.
     *
     * @param node             the current node
     * @param target           the current processor being invoked
     * @param traceInterceptor the trace interceptor
     * @param exchange         the current exchange
     * @return an optional return object to pass in the <tt>traceEventOut</tt> method.
     * @throws Exception is thrown if an error occurred during tracing
     */
    Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception;

    /**
     * Event called when an {@link Exchange} has been processed (out)
     * <p/>
     * This event is only called if trace out has been enabled.
     *
     * @param node             the current node
     * @param target           the current processor being invoked
     * @param traceInterceptor the trace interceptor
     * @param exchange         the current exchange (contains exception if the processing failed with an exception)
     * @param traceState       the optional object which was returned from the <tt>traceEventIn</tt> method.
     * @throws Exception is thrown if an error occurred during tracing
     */
    void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception;

}
