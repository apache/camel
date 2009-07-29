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

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.DelegateProcessor;

public class HandleFaultInterceptor extends DelegateProcessor {

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
        if (processor == null) {
            return;
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        handleFault(exchange);
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
