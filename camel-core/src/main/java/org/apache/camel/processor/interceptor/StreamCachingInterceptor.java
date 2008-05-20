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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.converter.stream.StreamCache;
import org.apache.camel.model.InterceptorRef;
import org.apache.camel.model.InterceptorType;
import org.apache.camel.processor.Interceptor;

/**
 * {@link Interceptor} that converts a message into a re-readable format
 */
public class StreamCachingInterceptor extends Interceptor {

    public StreamCachingInterceptor() {
        super();
        setInterceptorLogic(new Processor() {
            public void process(Exchange exchange) throws Exception {
                Object newBody = exchange.getIn().getBody(StreamCache.class);
                if (newBody != null) {
                    exchange.getIn().setBody(newBody);
                }
                proceed(exchange);
            }
        });
    }

    public StreamCachingInterceptor(Processor processor) {
        this();
        setProcessor(processor);
    }

    @Override
    public String toString() {
        return "StreamCachingInterceptor";
    }

    /**
     * Remove the {@link StreamCachingInterceptor} type of interceptor from the given list of interceptors
     *
     * @param interceptors the list of interceptors
     */
    public static void noStreamCaching(List<InterceptorType> interceptors) {
        for (int i = 0; i < interceptors.size(); i++) {
            InterceptorType interceptor = interceptors.get(i);
            if (interceptor instanceof InterceptorRef
                && ((InterceptorRef)interceptor).getInterceptor() instanceof StreamCachingInterceptor) {
                interceptors.remove(interceptor);
            }
        }
    }
}
