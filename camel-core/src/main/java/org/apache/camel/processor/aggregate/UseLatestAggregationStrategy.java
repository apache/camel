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
package org.apache.camel.processor.aggregate;

import org.apache.camel.Exchange;

import static org.apache.camel.util.ExchangeHelper.hasExceptionBeenHandledByErrorHandler;

/**
 * An {@link AggregationStrategy} which just uses the latest exchange which is useful
 * for status messages where old status messages have no real value. Another example is things
 * like market data prices, where old stock prices are not that relevant, only the current price is.
 *
 * @version 
 */
public class UseLatestAggregationStrategy implements AggregationStrategy {

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (newExchange == null) {
            return oldExchange;
        }
        if (oldExchange == null) {
            return newExchange;
        }

        Exchange answer = null;

        // propagate exception first
        propagateException(oldExchange, newExchange);
        if (newExchange.getException() != null) {
            answer = newExchange;
        }

        if (answer == null) {
            // the propagate failures
            answer = propagateFailure(oldExchange, newExchange);
        }

        return answer;
    }
    
    protected void propagateException(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return;
        }

        // propagate exception from old exchange if there isn't already an exception
        if (newExchange.getException() == null) {
            newExchange.setException(oldExchange.getException());
            newExchange.setProperty(Exchange.FAILURE_ENDPOINT, oldExchange.getProperty(Exchange.FAILURE_ENDPOINT));
        }
    }

    protected Exchange propagateFailure(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        // propagate exception from old exchange if there isn't already an exception
        boolean exceptionHandled = hasExceptionBeenHandledByErrorHandler(oldExchange);
        if (oldExchange.isFailed() || oldExchange.isRollbackOnly() || exceptionHandled) {
            // propagate failure by using old exchange as the answer
            return oldExchange;
        }

        return newExchange;
    }

    @Override
    public String toString() {
        return "UseLatestAggregationStrategy";
    }
}
