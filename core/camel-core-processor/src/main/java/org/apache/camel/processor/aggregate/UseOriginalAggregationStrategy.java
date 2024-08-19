/*
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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * An {@link AggregationStrategy} which just uses the original exchange which can be needed when you want to preserve
 * the original Exchange. For example when splitting an {@link Exchange} and then you may want to keep routing using the
 * original {@link Exchange}.
 *
 * @see org.apache.camel.processor.Splitter
 */
@Metadata(label = "bean",
          description = "An AggregationStrategy which just uses the original exchange which can be needed when you want to preserve"
                        + " the original Exchange. For example when splitting an Exchange and then you may want to keep routing using the"
                        + " original Exchange.",
          annotations = { "interfaceName=org.apache.camel.AggregationStrategy" })
@Configurer(metadataOnly = true)
public class UseOriginalAggregationStrategy implements AggregationStrategy {

    private final Exchange original;
    private final boolean propagateException;

    public UseOriginalAggregationStrategy() {
        this(null, true);
    }

    public UseOriginalAggregationStrategy(boolean propagateException) {
        this(null, propagateException);
    }

    public UseOriginalAggregationStrategy(Exchange original, boolean propagateException) {
        this.original = original;
        this.propagateException = propagateException;
    }

    /**
     * Creates a new instance as a clone of this strategy with the new given original.
     */
    public UseOriginalAggregationStrategy newInstance(Exchange original) {
        return new UseOriginalAggregationStrategy(original, propagateException);
    }

    @Override
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
            exception = checkCaughtException(oldExchange, newExchange);
            if (exception != null) {
                if (original != null) {
                    original.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
                } else {
                    oldExchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
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

    protected Exception checkCaughtException(Exchange oldExchange, Exchange newExchange) {
        Exception caught = newExchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (caught == null && oldExchange != null) {
            caught = oldExchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        }
        return caught;
    }

    public Exchange getOriginal() {
        return original;
    }

    @Override
    public String toString() {
        return "UseOriginalAggregationStrategy";
    }
}
