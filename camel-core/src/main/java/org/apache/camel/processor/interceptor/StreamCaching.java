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
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * {@link InterceptStrategy} implementation to configure stream caching on a RouteContext
 */
public final class StreamCaching implements InterceptStrategy {
    
    /*
     * Hide constructor -- instances will be created through static enable() methods
     */
    private StreamCaching() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Processor wrapProcessorInInterceptors(ProcessorType processorType, Processor target) throws Exception {
        return new StreamCachingInterceptor(target);
    }
    
    /**
     * Enable stream caching for a RouteContext
     * 
     * @param context the route context
     */
    public static void enable(RouteContext context) {
        for (InterceptStrategy strategy : context.getInterceptStrategies()) {
            if (strategy instanceof StreamCaching) {
                return;
            }
        }
        context.addInterceptStrategy(new StreamCaching());
    }
}
