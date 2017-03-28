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
import java.io.IOException;
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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Traceable;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a
 * href="http://camel.apache.org/splitter.html">Splitter</a> pattern
 * where an expression is evaluated to iterate through each of the parts of a
 * message and then each part is then send to some endpoint.
 *
 * @version 
 */
public class Splitter extends MulticastProcessor implements AsyncProcessor, Traceable {

    private final Expression expression;

    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy) {
        this(camelContext, expression, destination, aggregationStrategy, false, null, false, false, false, 0, null, false);
    }

    @Deprecated
    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy,
                    boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                    boolean streaming, boolean stopOnException, long timeout, Processor onPrepare, boolean useSubUnitOfWork) {
        this(camelContext, expression, destination, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService,
                streaming, stopOnException, timeout, onPrepare, useSubUnitOfWork, false);
    }

    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming, boolean stopOnException, long timeout, Processor onPrepare,
                    boolean useSubUnitOfWork, boolean parallelAggregate) {
        this(camelContext, expression, destination, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException, timeout,
             onPrepare, useSubUnitOfWork, false, false);
    }

    public Splitter(CamelContext camelContext, Expression expression, Processor destination, AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming, boolean stopOnException, long timeout, Processor onPrepare,
                    boolean useSubUnitOfWork, boolean parallelAggregate, boolean stopOnAggregateException) {
        super(camelContext, Collections.singleton(destination), aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException,
              timeout, onPrepare, useSubUnitOfWork, parallelAggregate, stopOnAggregateException);
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
            AggregationStrategy original = new UseOriginalAggregationStrategy(exchange, true);
            if (isShareUnitOfWork()) {
                original = new ShareUnitOfWorkAggregationStrategy(original);
            }
            setAggregationStrategyOnExchange(exchange, original);
        }

        return super.process(exchange, callback);
    }

    @Override
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) throws Exception {
        Object value = expression.evaluate(exchange, Object.class);
        if (exchange.getException() != null) {
            // force any exceptions occurred during evaluation to be thrown
            throw exchange.getException();
        }

        Iterable<ProcessorExchangePair> answer;
        if (isStreaming()) {
            answer = createProcessorExchangePairsIterable(exchange, value);
        } else {
            answer = createProcessorExchangePairsList(exchange, value);
        }
        if (exchange.getException() != null) {
            // force any exceptions occurred during creation of exchange paris to be thrown
            // before returning the answer;
            throw exchange.getException();
        }

        return answer;
    }

    private Iterable<ProcessorExchangePair> createProcessorExchangePairsIterable(final Exchange exchange, final Object value) {
        return new SplitterIterable(exchange, value);
    }

    private final class SplitterIterable implements Iterable<ProcessorExchangePair>, Closeable {

        // create a copy which we use as master to copy during splitting
        // this avoids any side effect reflected upon the incoming exchange
        final Object value;
        final Iterator<?> iterator;
        private final Exchange copy;
        private final RouteContext routeContext;
        private final Exchange original;

        private SplitterIterable(Exchange exchange, Object value) {
            this.original = exchange;
            this.value = value;
            this.iterator = ObjectHelper.createIterator(value);
            this.copy = copyExchangeNoAttachments(exchange, true);
            this.routeContext = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getRouteContext() : null;
        }

        @Override
        public Iterator<ProcessorExchangePair> iterator() {
            return new Iterator<ProcessorExchangePair>() {
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
                        try {
                            close();
                        } catch (IOException e) {
                            throw new RuntimeCamelException("Scanner aborted because of an IOException!", e);
                        }
                    }
                    return answer;
                }

                public ProcessorExchangePair next() {
                    Object part = iterator.next();
                    if (part != null) {
                        // create a correlated copy as the new exchange to be routed in the splitter from the copy
                        // and do not share the unit of work
                        Exchange newExchange = ExchangeHelper.createCorrelatedCopy(copy, false);
                        // If the splitter has an aggregation strategy
                        // then the StreamCache created by the child routes must not be
                        // closed by the unit of work of the child route, but by the unit of
                        // work of the parent route or grand parent route or grand grand parent route... (in case of nesting).
                        // Therefore, set the unit of work of the parent route as stream cache unit of work, if not already set.
                        if (newExchange.getProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK) == null) {
                            newExchange.setProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK, original.getUnitOfWork());
                        }
                        // if we share unit of work, we need to prepare the child exchange
                        if (isShareUnitOfWork()) {
                            prepareSharedUnitOfWork(newExchange, copy);
                        }
                        if (part instanceof Message) {
                            newExchange.setIn((Message) part);
                        } else {
                            Message in = newExchange.getIn();
                            in.setBody(part);
                        }
                        return createProcessorExchangePair(index++, getProcessors().iterator().next(), newExchange, routeContext);
                    } else {
                        return null;
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException("Remove is not supported by this iterator");
                }
            };
        }

        @Override
        public void close() throws IOException {
            if (value instanceof Scanner) {
                // special for Scanner which implement the Closeable since JDK7 
                Scanner scanner = (Scanner) value;
                scanner.close();
                IOException ioException = scanner.ioException();
                if (ioException != null) {
                    throw ioException;
                }
            } else if (value instanceof Closeable) {
                // we should throw out the exception here   
                IOHelper.closeWithException((Closeable) value);
            }
        }
       
    }

    private Iterable<ProcessorExchangePair> createProcessorExchangePairsList(Exchange exchange, Object value) {
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>();

        // reuse iterable and add it to the result list
        Iterable<ProcessorExchangePair> pairs = createProcessorExchangePairsIterable(exchange, value);
        try {
            for (ProcessorExchangePair pair : pairs) {
                if (pair != null) {
                    result.add(pair);
                }
            }
        } finally {
            if (pairs instanceof Closeable) {
                IOHelper.close((Closeable) pairs, "Splitter:ProcessorExchangePairs");
            }
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
            // non streaming mode, so we know the total size already
            exchange.setProperty(Exchange.SPLIT_SIZE, ((Collection<?>) allPairs).size());
        }
        if (it.hasNext()) {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(Exchange.SPLIT_COMPLETE, Boolean.TRUE);
            // streaming mode, so set total size when we are complete based on the index
            exchange.setProperty(Exchange.SPLIT_SIZE, index + 1);
        }
    }

    @Override
    protected Integer getExchangeIndex(Exchange exchange) {
        return exchange.getProperty(Exchange.SPLIT_INDEX, Integer.class);
    }

    public Expression getExpression() {
        return expression;
    }
    
    private static Exchange copyExchangeNoAttachments(Exchange exchange, boolean preserveExchangeId) {
        Exchange answer = ExchangeHelper.createCopy(exchange, preserveExchangeId);
        // we do not want attachments for the splitted sub-messages
        answer.getIn().setAttachmentObjects(null);
        // we do not want to copy the message history for splitted sub-messages
        answer.getProperties().remove(Exchange.MESSAGE_HISTORY);
        return answer;
    }
}
