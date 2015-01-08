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

/**
 * An {@link org.apache.camel.processor.aggregate.AggregationStrategy} which just uses the original exchange
 * which can be needed when you want to preserve the original Exchange. For example when splitting an {@link Exchange}
 * and then you may want to keep routing using the original {@link Exchange}.
 *
 * @see org.apache.camel.processor.Splitter
 * @version 
 */
public class UseOriginalAggregationStrategy implements AggregationStrategy {

    private final Exchange original;
    private final boolean propagateException;

    public UseOriginalAggregationStrategy() {
        this(null, true);
    }

    public UseOriginalAggregationStrategy(Exchange original, boolean propagateException) {
        this.original = original;
        this.propagateException = propagateException;
    }

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (propagateException) {
            Exception exception = checkException(oldExchange, newExchange);
            if (exception != null) {
                if (original != null) {
                    original.setException(exception);
                } else {
                    oldExchange.setException(exception);
                }
            }
        }
        return original != null ? original : oldExchange;
    }

    protected Exception checkException(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange.getException();
        } else {
            return (newExchange != null && newExchange.getException() != null)
                ? newExchange.getException()
                : oldExchange.getException();
        }
    }

    @Override
    public String toString() {
        return "UseOriginalAggregationStrategy";
    }
}