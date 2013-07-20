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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.util.MessageHelper;

/**
 * An interceptor that converts streams messages into a re-readable format
 * by wrapping the stream into a {@link StreamCache}.
 *
 * @deprecated no longer in use, will be removed in next Camel release.
 */
@Deprecated
public class StreamCachingInterceptor extends DelegateAsyncProcessor {

    public StreamCachingInterceptor() {
    }

    public StreamCachingInterceptor(Processor processor) {
        super(processor);
    }

    @Override
    public String toString() {
        return "StreamCachingInterceptor[" + processor + "]";
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        StreamCache newBody = exchange.getIn().getBody(StreamCache.class);
        if (newBody != null) {
            exchange.getIn().setBody(newBody);
        }
        MessageHelper.resetStreamCache(exchange.getIn());

        return processor.process(exchange, callback);
    }

}
