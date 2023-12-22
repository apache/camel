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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Traceable;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a href="http://camel.apache.org/splitter.html">Splitter</a> pattern where an expression is
 * evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.
 */
public class Splitter extends MulticastProcessor implements AsyncProcessor, Traceable {

    private static final Logger LOG = LoggerFactory.getLogger(Splitter.class);

    private static final String IGNORE_DELIMITER_MARKER = "false";
    private static final String SINGLE_DELIMITER_MARKER = "single";
    private final Expression expression;
    private final String delimiter;

    public Splitter(CamelContext camelContext, Route route, Expression expression, Processor destination,
                    AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                    boolean stopOnException, long timeout, Processor onPrepare,
                    boolean useSubUnitOfWork, boolean parallelAggregate) {
        this(camelContext, route, expression, destination, aggregationStrategy, parallelProcessing, executorService,
             shutdownExecutorService, streaming, stopOnException, timeout,
             onPrepare, useSubUnitOfWork, parallelAggregate, ",");
    }

    public Splitter(CamelContext camelContext, Route route, Expression expression, Processor destination,
                    AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                    boolean stopOnException, long timeout, Processor onPrepare,
                    boolean useSubUnitOfWork, boolean parallelAggregate, String delimiter) {
        super(camelContext, route, Collections.singleton(destination), aggregationStrategy, parallelProcessing, executorService,
              shutdownExecutorService, streaming, stopOnException,
              timeout, onPrepare, useSubUnitOfWork, parallelAggregate);
        this.expression = expression;
        StringHelper.notEmpty(delimiter, "delimiter");
        this.delimiter = delimiter;
        notNull(expression, "expression");
        notNull(destination, "destination");
    }

    @Override
    public String getTraceLabel() {
        return "split[" + expression + "]";
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        expression.init(getCamelContext());
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        AggregationStrategy strategy = getAggregationStrategy();

        // set original exchange if not already pre-configured
        if (strategy instanceof UseOriginalAggregationStrategy) {
            // need to create a new private instance, as we can also have concurrency issue so we cannot store state
            UseOriginalAggregationStrategy original = (UseOriginalAggregationStrategy) strategy;
            AggregationStrategy clone = original.newInstance(exchange);
            if (isShareUnitOfWork()) {
                clone = new ShareUnitOfWorkAggregationStrategy(clone);
            }
            setAggregationStrategyOnExchange(exchange, clone);
        }

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
    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange)
            throws Exception {

        Object value = expression.evaluate(exchange, Object.class);
        if (exchange.getException() != null) {
            // force any exceptions occurred during evaluation to be thrown
            throw exchange.getException();
        }

        Iterable<ProcessorExchangePair> answer = isStreaming()
                ? createProcessorExchangePairsIterable(exchange, value)
                : createProcessorExchangePairsList(exchange, value);
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
        private Exchange copy;
        private final Route route;
        private final Exchange original;

        private SplitterIterable() {
            // used for eager classloading
            value = null;
            iterator = null;
            copy = null;
            route = null;
            original = null;
            // for loading classes from iterator
            Object dummy = iterator();
            LOG.trace("Loaded {}", dummy.getClass().getName());
        }

        private SplitterIterable(Exchange exchange, Object value) {
            this.original = exchange;
            this.value = value;

            if (IGNORE_DELIMITER_MARKER.equalsIgnoreCase(delimiter)) {
                this.iterator = ObjectHelper.createIterator(value, null);
            } else if (SINGLE_DELIMITER_MARKER.equalsIgnoreCase(delimiter)) {
                // force single element
                this.iterator = ObjectHelper.createIterator(List.of(value));
            } else {
                this.iterator = ObjectHelper.createIterator(value, delimiter);
            }

            this.copy = copyAndPrepareSubExchange(exchange);
            this.route = ExchangeHelper.getRoute(exchange);
        }

        @Override
        public Iterator<ProcessorExchangePair> iterator() {
            return new Iterator<>() {
                private final Processor processor = getProcessors().iterator().next();
                private int index;
                private boolean closed;

                private Map<String, Object> txData;

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
                        Exchange newExchange = processorExchangeFactory.createCorrelatedCopy(copy, false);
                        newExchange.getExchangeExtension().setTransacted(original.isTransacted());
                        // If we are in a transaction, set TRANSACTION_CONTEXT_DATA property for new exchanges to share txData
                        // during the transaction.
                        if (original.isTransacted() && newExchange.getProperty(Exchange.TRANSACTION_CONTEXT_DATA) == null) {
                            if (txData == null) {
                                txData = new ConcurrentHashMap<>();
                            }
                            newExchange.setProperty(Exchange.TRANSACTION_CONTEXT_DATA, txData);
                        }
                        // If the splitter has an aggregation strategy
                        // then the StreamCache created by the child routes must not be
                        // closed by the unit of work of the child route, but by the unit of
                        // work of the parent route or grand parent route or grand grand parent route... (in case of nesting).
                        // Therefore, set the unit of work of the parent route as stream cache unit of work, if not already set.
                        if (newExchange.getProperty(ExchangePropertyKey.STREAM_CACHE_UNIT_OF_WORK) == null) {
                            newExchange.setProperty(ExchangePropertyKey.STREAM_CACHE_UNIT_OF_WORK, original.getUnitOfWork());
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
                        return createProcessorExchangePair(index++, processor, newExchange, route);
                    } else {
                        return null;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Remove is not supported by this iterator");
                }
            };
        }

        @Override
        public void close() throws IOException {
            if (copy != null) {
                processorExchangeFactory.release(copy);
                // null copy to avoid releasing it back again as close may be called multiple times
                copy = null;
                IOHelper.closeIterator(value);
            }
        }

    }

    private Iterable<ProcessorExchangePair> createProcessorExchangePairsList(Exchange exchange, Object value) {
        List<ProcessorExchangePair> result = new ArrayList<>();

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
    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs, boolean hasNext) {
        exchange.setProperty(ExchangePropertyKey.SPLIT_INDEX, index);
        if (allPairs instanceof Collection) {
            // non streaming mode, so we know the total size already
            exchange.setProperty(ExchangePropertyKey.SPLIT_SIZE, ((Collection<?>) allPairs).size());
        }
        if (hasNext) {
            exchange.setProperty(ExchangePropertyKey.SPLIT_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(ExchangePropertyKey.SPLIT_COMPLETE, Boolean.TRUE);
            // streaming mode, so set total size when we are complete based on the index
            exchange.setProperty(ExchangePropertyKey.SPLIT_SIZE, index + 1);
        }
    }

    @Override
    protected Integer getExchangeIndex(Exchange exchange) {
        return exchange.getProperty(ExchangePropertyKey.SPLIT_INDEX, Integer.class);
    }

    public Expression getExpression() {
        return expression;
    }

    private Exchange copyAndPrepareSubExchange(Exchange exchange) {
        Exchange answer = processorExchangeFactory.createCopy(exchange);
        // must preserve exchange id
        answer.setExchangeId(exchange.getExchangeId());
        if (exchange.getContext().isMessageHistory()) {
            // we do not want to copy the message history for split sub-messages
            answer.removeProperty(ExchangePropertyKey.MESSAGE_HISTORY);
        }
        return answer;
    }
}
