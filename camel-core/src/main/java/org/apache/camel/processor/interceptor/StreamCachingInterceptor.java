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
import org.apache.camel.StreamCache;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.util.MessageHelper;

/**
 * {@link DelegateProcessor} that converts a message into a re-readable format
 */
public class StreamCachingInterceptor extends DelegateProcessor {

    public StreamCachingInterceptor() {
        super();
    }

    public StreamCachingInterceptor(Processor processor) {
        this();
        setProcessor(processor);
    }

    @Override
    public String toString() {
        return "StreamCachingInterceptor[" + processor + "]";
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        StreamCache newBody = exchange.getIn().getBody(StreamCache.class);
        if (newBody != null) {
            exchange.getIn().setBody(newBody);
        }
        MessageHelper.resetStreamCache(exchange.getIn());

        getProcessor().process(exchange);
    }

}
