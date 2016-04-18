/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class HystrixProcessorCommand extends HystrixCommand<Exchange> {

    private final Exchange exchange;
    private final AsyncCallback callback;
    private final AsyncProcessor processor;
    private final AsyncProcessor fallback;

    public HystrixProcessorCommand(HystrixCommandGroupKey group, Exchange exchange, AsyncCallback callback,
                                   AsyncProcessor processor, AsyncProcessor fallback) {
        super(group);
        this.exchange = exchange;
        this.callback = callback;
        this.processor = processor;
        this.fallback = fallback;
    }

    @Override
    protected Exchange getFallback() {
        if (fallback != null) {
            try {
                Exception e = exchange.getException();
                // store the last to endpoint as the failure endpoint
                if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
                    exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
                }
                // give the rest of the pipeline another chance
                exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
                exchange.setException(null);
                // and we should not be regarded as exhausted as we are in a try .. catch block
                exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);

                fallback.process(exchange, callback);
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                callback.done(true);
            }
            return exchange;
        } else {
            return null;
        }
    }

    @Override
    protected Exchange run() throws Exception {
        try {
            processor.process(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }

        // if we failed then throw an exception
        if (exchange.getException() != null) {
            throw exchange.getException();
        }

        return exchange;
    }
}
