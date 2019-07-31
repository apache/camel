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

/**
 * The purpose of this interface is to allow an implementation to wrap
 * processors in a route with interceptors.  For example, a possible
 * usecase is to gather performance statistics at the processor's level.
 * <p/>
 * Its <b>strongly</b> adviced to use an {@link org.apache.camel.AsyncProcessor} as the returned wrapped
 * {@link Processor} which ensures the interceptor works well with the asynchronous routing engine.
 * You can use the {@link org.apache.camel.support.processor.DelegateAsyncProcessor} to easily return an
 * {@link org.apache.camel.AsyncProcessor} and override the
 * {@link org.apache.camel.AsyncProcessor#process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} to
 * implement your interceptor logic. And just invoke the super method to <b>continue</b> routing.
 */
public interface InterceptStrategy {

    /**
     * Give implementor an opportunity to wrap the target processor in a route.
     * <p/>
     * <b>Important:</b> See the class javadoc for advice on letting interceptor be compatible with the
     * asynchronous routing engine.
     *
     * @param context       Camel context
     * @param definition    the model this interceptor represents
     * @param target        the processor to be wrapped
     * @param nextTarget    the next processor to be routed to
     * @return processor    wrapped with an interceptor or not wrapped.
     * @throws Exception can be thrown
     */
    Processor wrapProcessorInInterceptors(CamelContext context, NamedNode definition,
                                          Processor target, Processor nextTarget) throws Exception;
}
