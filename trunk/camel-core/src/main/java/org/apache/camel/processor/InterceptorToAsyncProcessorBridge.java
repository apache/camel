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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * A bridge to have regular interceptors implemented as {@link org.apache.camel.Processor}
 * work with the asynchronous routing engine without causing side effects.
 *
 * @version 
 */
public class InterceptorToAsyncProcessorBridge extends ServiceSupport implements AsyncProcessor {

    private final AsyncProcessor interceptor;
    private volatile AsyncProcessor target;
    private volatile ThreadLocal<AsyncCallback> callback = new ThreadLocal<AsyncCallback>();
    private volatile ThreadLocal<Boolean> interceptorDone = new ThreadLocal<Boolean>();

    /**
     * Constructs the bridge
     *
     * @param interceptor the interceptor to bridge
     */
    public InterceptorToAsyncProcessorBridge(Processor interceptor) {
        this(interceptor, null);
    }

    /**
     * Constructs the bridge
     *
     * @param interceptor the interceptor to bridge
     * @param target the target
     */
    public InterceptorToAsyncProcessorBridge(Processor interceptor, AsyncProcessor target) {
        this.interceptor = AsyncProcessorConverterHelper.convert(interceptor);
        this.target = target;
    }

    /**
     * Process invoked by the interceptor
     * @param exchange the message exchange
     * @throws Exception
     */
    public void process(Exchange exchange) throws Exception {
        // invoke when interceptor wants to invoke
        boolean done = interceptor.process(exchange, callback.get());
        interceptorDone.set(done);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        // remember the callback to be used by the interceptor
        this.callback.set(callback);
        try {
            // invoke the target
            boolean done = target.process(exchange, callback);
            if (interceptorDone.get() != null) {
                // return the result from the interceptor if it was invoked
                return interceptorDone.get();
            } else {
                // otherwise from the target
                return done;
            }
        } finally {
            // cleanup
            this.callback.remove();
            this.interceptorDone.remove();
        }
    }

    public void setTarget(Processor target) {
        this.target = AsyncProcessorConverterHelper.convert(target);
    }

    @Override
    public String toString() {
        return "AsyncBridge[" + interceptor.toString() + "]";
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(target, interceptor);
    }

    @Override
    protected void doStop() throws Exception {
        callback.remove();
        interceptorDone.remove();
        ServiceHelper.stopServices(interceptor, target);
    }
}
