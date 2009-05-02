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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 * {@link InterceptStrategy} implementation to configure stream caching on a RouteContext
 */
public final class StreamCaching implements InterceptStrategy {
    
    @SuppressWarnings("unchecked")
    public Processor wrapProcessorInInterceptors(ProcessorDefinition processorDefinition, Processor target, Processor nextTarget) throws Exception {
        return new StreamCachingInterceptor(target);
    }
    
    /**
     * A helper method to return the StreamCaching instance
     * for a given {@link org.apache.camel.CamelContext} if one is enabled
     *
     * @param context the camel context the stream cache is connected to
     * @return the stream cache or null if none can be found
     */
    public static StreamCaching getStreamCaching(CamelContext context) {
        List<InterceptStrategy> list = context.getInterceptStrategies();
        for (InterceptStrategy interceptStrategy : list) {
            if (interceptStrategy instanceof StreamCaching) {
                return (StreamCaching)interceptStrategy;
            }
        }
        return null;
    }

}
