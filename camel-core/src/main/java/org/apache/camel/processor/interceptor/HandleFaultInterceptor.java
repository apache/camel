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
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.util.AsyncProcessorHelper;

public class HandleFaultInterceptor extends DelegateProcessor implements AsyncProcessor {

    public HandleFaultInterceptor() {
        super();
    }

    public HandleFaultInterceptor(Processor processor) {
        this();
        setProcessor(processor);
    }

    @Override
    public String toString() {
        return "HandleFaultInterceptor[" + processor + "]";
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        if (processor == null) {
            // no processor so nothing to process, so return
            callback.done(true);
            return true;
        }

        if (processor instanceof AsyncProcessor) {
            return ((AsyncProcessor) processor).process(exchange, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    handleFault(exchange);
                    callback.done(doneSynchronously);
                }
            });
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        handleFault(exchange);

        callback.done(true);
        return true;
    }

    /**
     * Handles the fault message by converting it to an Exception
     */
    protected void handleFault(Exchange exchange) {
        // Take the fault message out before we keep on going
        if (exchange.hasFault()) {
            final Object faultBody = exchange.getFault().getBody();
            if (faultBody != null && exchange.getException() == null) {
                // remove fault as we are converting it to an exception
                exchange.removeFault();
                if (faultBody instanceof Exception) {
                    exchange.setException((Exception) faultBody);
                } else {
                    // wrap it in an exception
                    exchange.setException(new CamelException(faultBody.toString()));
                }
            }
        }
    }

}
