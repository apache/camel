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
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The processor which sends messages in a loop.
 *
 * @version 
 */
public class LoopProcessor extends DelegateAsyncProcessor implements Traceable {
    private static final Logger LOG = LoggerFactory.getLogger(LoopProcessor.class);

    private final Expression expression;

    public LoopProcessor(Expression expression, Processor processor) {
        super(processor);
        this.expression = expression;
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

        // set the size before we start
        exchange.setProperty(Exchange.LOOP_SIZE, count);

        // loop synchronously
        while (index.get() < count.get()) {

            // and prepare for next iteration
            ExchangeHelper.prepareOutToIn(exchange);
            boolean sync = process(exchange, callback, index, count);

            if (!sync) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the routing slip will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());

            // increment counter before next loop
            index.getAndIncrement();
        }

        // we are done so prepare the result
        ExchangeHelper.prepareOutToIn(exchange);
        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    protected boolean process(final Exchange exchange, final AsyncCallback callback,
                              final AtomicInteger index, final AtomicInteger count) {

        // set current index as property
        LOG.debug("LoopProcessor: iteration #{}", index.get());
        exchange.setProperty(Exchange.LOOP_INDEX, index.get());

        boolean sync = processNext(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // we only have to handle async completion of the routing slip
                if (doneSync) {
                    return;
                }

                // increment index as we have just processed once
                index.getAndIncrement();

                // continue looping asynchronously
                while (index.get() < count.get()) {

                    // and prepare for next iteration
                    ExchangeHelper.prepareOutToIn(exchange);

                    // process again
                    boolean sync = process(exchange, callback, index, count);
                    if (!sync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                        // the remainder of the routing slip will be completed async
                        // so we break out now, then the callback will be invoked which then continue routing from where we left here
                        return;
                    }

                    // increment counter before next loop
                    index.getAndIncrement();
                }

                // we are done so prepare the result
                ExchangeHelper.prepareOutToIn(exchange);
                LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                callback.done(false);
            }
        });

        return sync;
    }

    @Override
    public String toString() {
        return "Loop[for: " + expression + " times do: " + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "loop[" + expression + "]";
    }

    public Expression getExpression() {
        return expression;
    }
}
