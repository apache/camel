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
package org.apache.camel.component.cxf;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.AsyncCallback;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;

/**
 * A simple class to wrap an existing processor (synchronous or asynchronous)
 * with two synchronous processor that will be executed before and after the
 * main processor.
 */
public class AsyncProcessorDecorator implements AsyncProcessor {

    private final AsyncProcessor processor;
    private final Processor before;
    private final Processor after;

    public AsyncProcessorDecorator(Processor processor, Processor before, Processor after) {
        this.processor = AsyncProcessorTypeConverter.convert(processor);
        this.before = before;
        this.after = after;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            before.process(exchange);
        } catch (Throwable t) {
            exchange.setException(t);
            callback.done(true);
            return true;
        }
        return processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSynchronously) {
                try {
                    after.process(exchange);
                    callback.done(doneSynchronously);
                } catch (Throwable t) {
                    exchange.setException(t);
                }
            }
        });
    }

}
