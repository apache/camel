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

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;

/**
 * Factory to create the trace interceptor.
 */
@Deprecated
public interface TraceInterceptorFactory {

    /**
     * Create a trace interceptor.
     * <p/>
     * It is expected that the factory will create a subclass of {@link TraceInterceptor},
     * however any Processor will suffice.
     * <p/>
     * Use this factory to take more control of how trace events are persisted.
     *
     * @param node      the current node
     * @param target    the current target
     * @param formatter the trace formatter
     * @param tracer    the tracer
     * @return the created trace interceptor
     */
    Processor createTraceInterceptor(ProcessorDefinition<?> node, Processor target, TraceFormatter formatter, Tracer tracer);

}
