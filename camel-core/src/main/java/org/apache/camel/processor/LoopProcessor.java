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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The processor which sends messages in a loop.
 */
public class LoopProcessor extends DelegateAsyncProcessor implements Traceable {
    private static final Logger LOG = LoggerFactory.getLogger(LoopProcessor.class);

    private final Expression expression;
    private final boolean copy;

    public LoopProcessor(Processor processor, Expression expression, boolean copy) {
        super(processor);
        this.expression = expression;
        this.copy = copy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // use atomic integer to be able to pass reference and keep track on the values
        AtomicInteger index = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();

        // Intermediate conversion to String is needed when direct conversion to Integer is not available
        // but evaluation result is a textual representation of a numeric value.
        String text = expression.evaluate(exchange, String.class);
        try {
            int num = ExchangeHelper.convertToMandatoryType(exchange, Integer.class, text);
            count.set(num);
        } catch (NoTypeConversionAvailableException e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        Exchange target = exchange;

        // set the size before we start
        exchange.setProperty(Exchange.LOOP_SIZE, count);

        // loop synchronously
        while (index.get() < count.get()) {

            // and prepare for next iteration
            target = prepareExchange(exchange, index.get());
            boolean sync = process(target, callback, index, count);

            if (!sync) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", target.getExchangeId());
                // the remainder of the routing slip will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", target.getExchangeId());

            // increment counter before next loop
            index.getAndIncrement();
        }

        // we are done so prepare the result
        ExchangeHelper.copyResults(exchange, target);
        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    protected boolean process(final Exchange exchange, final AsyncCallback callback,
                              final AtomicInteger index, final AtomicInteger count) {

        // set current index as property
        LOG.debug("LoopProcessor: iteration #{}", index.get());
        exchange.setProperty(Exchange.LOOP_INDEX, index.get());

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the routing slip
                if (doneSync) {
                    return;
                }

                Exchange target = exchange;

                // increment index as we have just processed once
                index.getAndIncrement();

                // continue looping asynchronously
                while (index.get() < count.get()) {

                    // and prepare for next iteration
                    target = prepareExchange(exchange, index.get());

                    // process again
                    boolean sync = process(target, callback, index, count);
                    if (!sync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", target.getExchangeId());
                        // the remainder of the routing slip will be completed async
                        // so we break out now, then the callback will be invoked which then continue routing from where we left here
                        return;
                    }

                    // increment counter before next loop
                    index.getAndIncrement();
                }

                // we are done so prepare the result
                ExchangeHelper.copyResults(exchange, target);
                LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        });

        return sync;
    }

    /**
     * Prepares the exchange for the next iteration
     *
     * @param exchange the exchange
     * @param index the index of the next iteration
     * @return the exchange to use
     */
    protected Exchange prepareExchange(Exchange exchange, int index) {
        if (copy) {
            // use a copy but let it reuse the same exchange id so it appear as one exchange
            return ExchangeHelper.createCopy(exchange, true);
        } else {
            ExchangeHelper.prepareOutToIn(exchange);
            return exchange;
        }
    }

    public Expression getExpression() {
        return expression;
    }

    public String getTraceLabel() {
        return "loop[" + expression + "]";
    }

    @Override
    public String toString() {
        return "Loop[for: " + expression + " times do: " + getProcessor() + "]";
    }
}
