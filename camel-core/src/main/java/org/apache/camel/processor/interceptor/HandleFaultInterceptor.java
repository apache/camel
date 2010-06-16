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
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.DelegateAsyncProcessor;

public class HandleFaultInterceptor extends DelegateAsyncProcessor {

    public HandleFaultInterceptor() {
        super();
    }

    public HandleFaultInterceptor(Processor processor) {
        super(processor);
    }

    @Override
    public String toString() {
        return "HandleFaultInterceptor[" + processor + "]";
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        return getProcessor().process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                try {
                    // handle fault after we are done
                    handleFault(exchange);
                } finally {
                    // and let the original callback know we are done as well
                    callback.done(doneSync);
                }
            }
        });
    }

    /**
     * Handles the fault message by converting it to an Exception
     */
    protected void handleFault(Exchange exchange) {
        // Take the fault message out before we keep on going
        if (exchange.hasOut() && exchange.getOut().isFault()) {
            final Object faultBody = exchange.getOut().getBody();
            if (faultBody != null && exchange.getException() == null) {
                // remove fault as we are converting it to an exception
                exchange.setOut(null);
                if (faultBody instanceof Throwable) {
                    exchange.setException((Throwable) faultBody);
                } else {
                    // wrap it in an exception
                    exchange.setException(new CamelException(faultBody.toString()));
                }
            }
        }
    }

}
