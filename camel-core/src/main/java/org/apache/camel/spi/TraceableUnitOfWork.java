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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.Processor;

/**
 * A Unit of work that is also traceable with the
 * {@link org.apache.camel.processor.interceptor.TraceInterceptor} so we can trace the excact
 * route path a given {@link org.apache.camel.Exchange} has been processed.
 *
 * @version $Revision$
 */
public interface TraceableUnitOfWork extends UnitOfWork {

    /**
     * Adds the given processor that was intercepted
     *
     * @param processor the processor
     */
    void addInterceptedProcessor(Processor processor);

    /**
     * Gets the last intercepted processor, is <tt>null</tt> if no last exists.
     */
    Processor getLastInterceptedProcessor();

    /**
     * Gets the 2nd last intercepted processor, is <tt>null</tt> if no last exists.
     */
    Processor getSecondLastInterceptedProcessor();

    /**
     * Gets the current list of intercepted processors, representing the route path the
     * current {@link org.apache.camel.Exchange} has taken.
     */
    List<Processor> getInterceptedProcessors();

}
