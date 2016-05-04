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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * The processor which sends messages in a loop.
 */
public class LoopProcessor extends DelegateAsyncProcessor implements Traceable, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(LoopProcessor.class);

    private String id;
    private final Expression expression;
    private final Predicate predicate;
    private final boolean copy;

    public LoopProcessor(Processor processor, Expression expression, Predicate predicate, boolean copy) {
        super(processor);
        this.expression = expression;
        this.predicate = predicate;
        this.copy = copy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // use atomic integer to be able to pass reference and keep track on the values
        AtomicInteger index = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        AtomicBoolean doWhile = new AtomicBoolean();

        try {
            if (expression != null) {
                // Intermediate conversion to String is needed when direct conversion to Integer is not available
                // but evaluation result is a textual representation of a numeric value.
                String text = expression.evaluate(exchange, String.class);
                int num = ExchangeHelper.convertToMandatoryType(exchange, Integer.class, text);
                count.set(num);
            } else {
                boolean result = predicate.matches(exchange);
                doWhile.set(result);
            }
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // we hold on to the original Exchange in case it's needed for copies
        final Exchange original = exchange;
        
        // per-iteration exchange
        Exchange target = exchange;

        // set the size before we start
        if (predicate == null) {
            exchange.setProperty(Exchange.LOOP_SIZE, count);
        }

        // loop synchronously
        while ((predicate != null && doWhile.get()) || (index.get() < count.get())) {

            // and prepare for next iteration
            // if (!copy) target = exchange; else copy of original
            target = prepareExchange(exchange, index.get(), original);
            // the following process method will in the done method re-evaluate the predicate
            // so we do not need to do it here as well
            boolean sync = process(target, callback, index, count, doWhile, original);

            if (!sync) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", target.getExchangeId());
                // the remainder of the loop will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", target.getExchangeId());

            // check for error if so we should break out
            if (!continueProcessing(target, "so breaking out of loop", LOG)) {
                break;
            }
        }

        // we are done so prepare the result
        ExchangeHelper.copyResults(exchange, target);
        LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        callback.done(true);
        return true;
    }

    protected boolean process(final Exchange exchange, final AsyncCallback callback,
                              final AtomicInteger index, final AtomicInteger count, final AtomicBoolean doWhile,
                              final Exchange original) {

        // set current index as property
        LOG.debug("LoopProcessor: iteration #{}", index.get());
        exchange.setProperty(Exchange.LOOP_INDEX, index.get());

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // increment counter after done
                index.getAndIncrement();

                // evaluate predicate for next loop
                if (predicate != null && index.get() > 0) {
                    try {
                        boolean result = predicate.matches(exchange);
                        doWhile.set(result);
                    } catch (Exception e) {
                        // break out looping due that exception
                        exchange.setException(e);
                        doWhile.set(false);
                    }
                }

                // we only have to handle async completion of the loop
                // (as the sync is done in the outer processor)
                if (doneSync) {
                    return;
                }

                Exchange target = exchange;

                // continue looping asynchronously
                while ((predicate != null && doWhile.get()) || (index.get() < count.get())) {

                    // check for error if so we should break out
                    if (!continueProcessing(target, "so breaking out of loop", LOG)) {
                        break;
                    }

                    // and prepare for next iteration
                    target = prepareExchange(exchange, index.get(), original);

                    // process again
                    boolean sync = process(target, callback, index, count, doWhile, original);
                    if (!sync) {
                        LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", target.getExchangeId());
                        // the remainder of the routing slip will be completed async
                        // so we break out now, then the callback will be invoked which then continue routing from where we left here
                        return;
                    }
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
    protected Exchange prepareExchange(Exchange exchange, int index, Exchange original) {
        if (copy) {
            // use a copy but let it reuse the same exchange id so it appear as one exchange
            // use the original exchange rather than the looping exchange (esp. with the async routing engine)
            return ExchangeHelper.createCopy(original, true);
        } else {
            ExchangeHelper.prepareOutToIn(exchange);
            return exchange;
        }
    }

    public Expression getExpression() {
        return expression;
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public boolean isCopy() {
        return copy;
    }

    public String getTraceLabel() {
        if (predicate != null) {
            return "loopWhile[" + predicate + "]";
        } else {
            return "loop[" + expression + "]";
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        if (predicate != null) {
            return "Loop[while: " + predicate + " do: " + getProcessor() + "]";
        } else {
            return "Loop[for: " + expression + " times do: " + getProcessor() + "]";
        }
    }
}
