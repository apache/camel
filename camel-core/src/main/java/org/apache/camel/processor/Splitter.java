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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a
 * href="http://camel.apache.org/splitter.html">Splitter</a> pattern
 * where an expression is evaluated to iterate through each of the parts of a
 * message and then each part is then send to some endpoint.
 *
 * @version $Revision$
 */
public class Splitter extends MulticastProcessor implements AsyncProcessor, Traceable {
    private static final transient Log LOG = LogFactory.getLog(Splitter.class);

    private final Expression expression;

    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy) {
        this(camelContext, expression, destination, aggregationStrategy, false, null, false, false, 0);
    }

    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy,
                    boolean parallelProcessing, ExecutorService executorService, boolean streaming, boolean stopOnException, long timeout) {
        super(camelContext, Collections.singleton(destination), aggregationStrategy, parallelProcessing, executorService, streaming, stopOnException, timeout);

        this.expression = expression;
        notNull(expression, "expression");
        notNull(destination, "destination");
    }

    @Override
    public String toString() {
        return "Splitter[on: " + expression + " to: " + getProcessors().iterator().next() + " aggregate: " + getAggregationStrategy() + "]";
    }

    @Override
    public String getTraceLabel() {
        return "split[" + expression + "]";
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        final AggregationStrategy strategy = getAggregationStrategy();

        // if no custom aggregation strategy is being used then fallback to keep the original
        // and propagate exceptions which is done by a per exchange specific aggregation strategy
        // to ensure it supports async routing
        if (strategy == null) {
            UseOriginalAggregationStrategy original = new UseOriginalAggregationStrategy(exchange, true);
            setAggregationStrategyOnExchange(exchange, original);
        }

        return super.process(exchange, callback);
    }

    @Override
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) {
        Object value = expression.evaluate(exchange, Object.class);

        if (isStreaming()) {
            return createProcessorExchangePairsIterable(exchange, value);
        } else {
            return createProcessorExchangePairsList(exchange, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Iterable<ProcessorExchangePair> createProcessorExchangePairsIterable(final Exchange exchange, final Object value) {
        final Iterator iterator = ObjectHelper.createIterator(value);
        return new Iterable() {
            // create a copy which we use as master to copy during splitting
            // this avoids any side effect reflected upon the incoming exchange
            private final Exchange copy = ExchangeHelper.createCopy(exchange, true);

            public Iterator iterator() {
                return new Iterator() {
                    private int index;
                    private boolean closed;

                    public boolean hasNext() {
                        if (closed) {
                            return false;
                        }

                        boolean answer = iterator.hasNext();
                        if (!answer) {
                            // we are now closed
                            closed = true;
                            // nothing more so we need to close the expression value in case it needs to be
                            if (value instanceof Closeable) {
                                IOHelper.close((Closeable) value, value.getClass().getName(), LOG);
                            } else if (value instanceof Scanner) {
                                // special for Scanner as it does not implement Closeable
                                ((Scanner) value).close();
                            }
                        }
                        return answer;
                    }

                    public Object next() {
                        Object part = iterator.next();
                        // create a copy as the new exchange to be routed in the splitter from the copy
                        Exchange newExchange = ExchangeHelper.createCopy(copy, true);
                        if (part instanceof Message) {
                            newExchange.setIn((Message)part);
                        } else {
                            Message in = newExchange.getIn();
                            in.setBody(part);
                        }
                        return createProcessorExchangePair(index++, getProcessors().iterator().next(), newExchange);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Remove is not supported by this iterator");
                    }
                };
            }

        };
    }

    private Iterable<ProcessorExchangePair> createProcessorExchangePairsList(Exchange exchange, Object value) {
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>();

        // reuse iterable and add it to the result list
        Iterable<ProcessorExchangePair> pairs = createProcessorExchangePairsIterable(exchange, value);
        for (ProcessorExchangePair pair : pairs) {
            result.add(pair);
        }

        return result;
    }

    @Override
    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs,
                                     Iterator<ProcessorExchangePair> it) {
        // do not share unit of work
        exchange.setUnitOfWork(null);

        exchange.setProperty(Exchange.SPLIT_INDEX, index);
        if (allPairs instanceof Collection) {
            exchange.setProperty(Exchange.SPLIT_SIZE, ((Collection<?>)allPairs).size());
        }
        if (it.hasNext()) {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.TRUE);
        }
    }

    public Expression getExpression() {
        return expression;
    }
}
