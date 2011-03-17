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

import org.apache.camel.Exchange;
import org.slf4j.Logger;

import static org.apache.camel.util.ExchangeHelper.hasExceptionBeenHandledByErrorHandler;

/**
 * Helper for processing {@link org.apache.camel.Exchange} in a
 * <a href="http://camel.apache.org/pipes-and-filters.html">pipeline</a>.
 *
 * @version 
 */
public final class PipelineHelper {

    private PipelineHelper() {
    }

    /**
     * Should we continue processing the exchange?
     *
     * @param exchange the next exchange
     * @param message a message to use when logging that we should not continue processing
     * @param log a logger
     * @return <tt>true</tt> to continue processing, <tt>false</tt> to break out, for example if an exception occurred.
     */
    public static boolean continueProcessing(Exchange exchange, String message, Logger log) {
        // check for error if so we should break out
        boolean exceptionHandled = hasExceptionBeenHandledByErrorHandler(exchange);
        if (exchange.isFailed() || exchange.isRollbackOnly() || exceptionHandled) {
            // The Exchange.ERRORHANDLED_HANDLED property is only set if satisfactory handling was done
            // by the error handler. It's still an exception, the exchange still failed.
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Message exchange has failed " + message + " for exchange: ").append(exchange);
                if (exchange.isRollbackOnly()) {
                    sb.append(" Marked as rollback only.");
                }
                if (exchange.getException() != null) {
                    sb.append(" Exception: ").append(exchange.getException());
                }
                if (exchange.hasOut() && exchange.getOut().isFault()) {
                    sb.append(" Fault: ").append(exchange.getOut());
                }
                if (exceptionHandled) {
                    sb.append(" Handled by the error handler.");
                }
                log.debug(sb.toString());
            }

            return false;
        }


        // check for stop
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, exchange, stop);
            if (doStop) {
                if (log.isDebugEnabled()) {
                    log.debug("ExchangeId: " + exchange.getExchangeId() + " is marked to stop routing: " + exchange);
                }
                return false;
            }
        }

        return true;
    }

}
