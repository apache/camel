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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.jspecify.annotations.Nullable;

/**
 * Strategy that wraps the {@link Processor}s in a route with interceptors, for cross-cutting concerns such as gathering
 * performance statistics at the processor level.
 * <p/>
 * Camel invokes {@link #wrapProcessorInInterceptors} for each processor while building a route, giving the strategy the
 * chance to return a wrapping processor (or the original). Registered strategies are applied to every interceptable
 * processor; a processor can opt out by implementing {@link InterceptableProcessor}.
 * <p/>
 * It is <b>strongly</b> advised to return an {@link org.apache.camel.AsyncProcessor} as the wrapper, which ensures the
 * interceptor works well with the asynchronous routing engine. You can use
 * {@link org.apache.camel.support.processor.DelegateAsyncProcessor} to easily return an
 * {@link org.apache.camel.AsyncProcessor} and override
 * {@link org.apache.camel.AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} to
 * implement your interceptor logic, then invoke the super method to <b>continue</b> routing.
 *
 * @see InterceptableProcessor
 */
public interface InterceptStrategy {

    /**
     * Give implementor an opportunity to wrap the target processor in a route.
     * <p/>
     * <b>Important:</b> See the class javadoc for advice on letting interceptor be compatible with the asynchronous
     * routing engine.
     *
     * @param  context    Camel context
     * @param  definition the model this interceptor represents
     * @param  target     the processor to be wrapped
     * @param  nextTarget the next processor to be routed to
     * @return            processor wrapped with an interceptor or not wrapped.
     * @throws Exception  can be thrown
     */
    Processor wrapProcessorInInterceptors(
            CamelContext context, NamedNode definition,
            Processor target, @Nullable Processor nextTarget)
            throws Exception;
}
