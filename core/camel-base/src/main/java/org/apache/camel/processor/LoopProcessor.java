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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * The processor which sends messages in a loop.
 */
public class LoopProcessor extends DelegateAsyncProcessor implements Traceable, IdAware {

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
        try {

            LoopState state = new LoopState(exchange, callback);

            if (exchange.isTransacted()) {
                exchange.getContext().getReactiveExecutor().scheduleSync(state);
            } else {
                exchange.getContext().getReactiveExecutor().scheduleMain(state);
            }
            return false;

        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    /**
     * Class holding state for loop processing
     */
    class LoopState implements Runnable {

        final Exchange exchange;
        final AsyncCallback callback;
        Exchange current;
        int index;
        int count;

        public LoopState(Exchange exchange, AsyncCallback callback) throws NoTypeConversionAvailableException {
            this.exchange = exchange;
            this.callback = callback;
            this.current = exchange;

            // evaluate expression / predicate
            if (expression != null) {
                // Intermediate conversion to String is needed when direct conversion to Integer is not available
                // but evaluation result is a textual representation of a numeric value.
                String text = expression.evaluate(exchange, String.class);
                count = ExchangeHelper.convertToMandatoryType(exchange, Integer.class, text);
                exchange.setProperty(Exchange.LOOP_SIZE, count);
            }
        }

        @Override
        public void run() {
            try {
                // check for error if so we should break out
                boolean cont = continueProcessing(current, "so breaking out of loop", log);
                boolean doWhile = predicate == null || predicate.matches(current);
                boolean doLoop = expression == null || index < count;

                // iterate
                if (cont && doWhile && doLoop) {
                    // and prepare for next iteration
                    current = prepareExchange(exchange, index);

                    // set current index as property
                    log.debug("LoopProcessor: iteration #{}", index);
                    current.setProperty(Exchange.LOOP_INDEX, index);

                    processor.process(current, doneSync -> {
                        // increment counter after done
                        index++;
                        exchange.getContext().getReactiveExecutor().schedule(this);
                    });
                } else {
                    // we are done so prepare the result
                    ExchangeHelper.copyResults(exchange, current);
                    log.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                    callback.done(false);
                }
            } catch (Exception e) {
                log.trace("Processing failed for exchangeId: {} >>> {}", exchange.getExchangeId(), e.getMessage());
                exchange.setException(e);
                callback.done(false);
            }
        }

        @Override
        public String toString() {
            return "LoopState[" + exchange.getExchangeId() + "]";
        }
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
            // use the original exchange rather than the looping exchange (esp. with the async routing engine)
            return ExchangeHelper.createCopy(exchange, true);
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

    @Override
    public String getTraceLabel() {
        if (predicate != null) {
            return "loopWhile[" + predicate + "]";
        } else {
            return "loop[" + expression + "]";
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
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
