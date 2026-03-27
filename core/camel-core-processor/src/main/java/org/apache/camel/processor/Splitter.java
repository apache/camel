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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SplitResult;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.GroupIterator;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a dynamic <a href="http://camel.apache.org/splitter.html">Splitter</a> pattern where an expression is
 * evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.
 */
public class Splitter extends MulticastProcessor {

    private static final String IGNORE_DELIMITER_MARKER = "false";
    private static final String SINGLE_DELIMITER_MARKER = "single";
    private static final String SPLIT_FAILURE_TRACKER = "CamelSplitFailureTracker";
    private static final String SPLIT_WATERMARK_OFFSET = "CamelSplitWatermarkOffset";
    private static final String SPLIT_WATERMARK_COUNT = "CamelSplitWatermarkCount";
    private final Expression expression;
    private final String delimiter;
    private int group;
    private double errorThreshold;
    private int maxFailedRecords;
    private Map<String, String> watermarkStore;
    private String watermarkKey;
    private Expression watermarkExpression;

    public Splitter(CamelContext camelContext, Route route, Expression expression, Processor destination,
                    AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                    boolean stopOnException, long timeout, Processor onPrepare,
                    boolean shareUnitOfWork, boolean parallelAggregate) {
        this(camelContext, route, expression, destination, aggregationStrategy, parallelProcessing, executorService,
             shutdownExecutorService, streaming, stopOnException, timeout,
             onPrepare, shareUnitOfWork, parallelAggregate, ",");
    }

    public Splitter(CamelContext camelContext, Route route, Expression expression, Processor destination,
                    AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                    ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                    boolean stopOnException, long timeout, Processor onPrepare,
                    boolean shareUnitOfWork, boolean parallelAggregate, String delimiter) {
        super(camelContext, route, Collections.singleton(destination), aggregationStrategy, parallelProcessing, executorService,
              shutdownExecutorService, streaming, stopOnException,
              timeout, onPrepare, shareUnitOfWork, parallelAggregate, 0);
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
        if (watermarkExpression != null) {
            watermarkExpression.init(getCamelContext());
        }
    }

    @Override
    public boolean process(Exchange exchange, final AsyncCallback callback) {
        AggregationStrategy strategy = getAggregationStrategy();

        // set original exchange if not already pre-configured
        if (strategy instanceof UseOriginalAggregationStrategy original) {
            // need to create a new private instance, as we can also have concurrency issue so we cannot store state
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

        // create failure tracker if error thresholds are configured
        boolean hasErrorThreshold = errorThreshold > 0 || maxFailedRecords > 0;
        if (hasErrorThreshold) {
            exchange.setProperty(SPLIT_FAILURE_TRACKER, new SplitFailureTracker());
        }

        // set current watermark value as exchange property before processing
        boolean hasWatermark = watermarkStore != null && watermarkKey != null;
        if (hasWatermark) {
            String currentWatermark = watermarkStore.get(watermarkKey);
            if (currentWatermark != null) {
                exchange.setProperty(Exchange.SPLIT_WATERMARK, currentWatermark);
            }
        }

        // wrap callback to build SplitResult and/or update watermark after all items are processed
        boolean needsWrapping = hasErrorThreshold || hasWatermark;
        AsyncCallback wrappedCallback = needsWrapping
                ? doneSync -> {
                    if (hasErrorThreshold) {
                        buildSplitResult(exchange);
                    }
                    if (hasWatermark) {
                        updateWatermark(exchange);
                    }
                    callback.done(doneSync);
                }
                : callback;

        return super.process(exchange, wrappedCallback);
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

        // store total items count in tracker for SplitResult reporting
        if (answer instanceof Collection<?> coll) {
            SplitFailureTracker tracker = exchange.getProperty(SPLIT_FAILURE_TRACKER, SplitFailureTracker.class);
            if (tracker != null) {
                tracker.setTotalItems(coll.size());
            }
        }

        return answer;
    }

    private Iterable<ProcessorExchangePair> createProcessorExchangePairsIterable(final Exchange exchange, final Object value) {
        return new SplitterIterable(exchange, value);
    }

    private final class SplitterIterable implements Iterable<ProcessorExchangePair>, Closeable {

        // create a copy which we use as master to copy during splitting
        // this avoids any side effect reflected upon the incoming exchange
        private final Object value;
        private final Iterator<?> iterator;
        private Exchange copy;
        private final Route route;
        private final Exchange original;
        private final int watermarkOffset;

        private SplitterIterable(Exchange exchange, Object value) {
            this.original = exchange;
            this.value = value;

            Iterator<?> rawIterator;
            if (IGNORE_DELIMITER_MARKER.equalsIgnoreCase(delimiter)) {
                rawIterator = ObjectHelper.createIterator(value, null);
            } else if (SINGLE_DELIMITER_MARKER.equalsIgnoreCase(delimiter)) {
                // force single element
                rawIterator = ObjectHelper.createIterator(List.of(value));
            } else {
                rawIterator = ObjectHelper.createIterator(value, delimiter);
            }

            // index-based watermarking: skip items up to the stored watermark index
            int skipCount = 0;
            if (watermarkStore != null && watermarkKey != null && watermarkExpression == null) {
                String storedIndex = watermarkStore.get(watermarkKey);
                if (storedIndex != null) {
                    int skipTo = Integer.parseInt(storedIndex);
                    while (rawIterator.hasNext() && skipCount <= skipTo) {
                        rawIterator.next();
                        skipCount++;
                    }
                }
            }
            this.watermarkOffset = skipCount;
            if (skipCount > 0) {
                exchange.setProperty(SPLIT_WATERMARK_OFFSET, skipCount);
            }

            // wrap with GroupIterator if group > 0 to chunk items into List batches
            this.iterator = group > 0 ? new GroupIterator(rawIterator, group) : rawIterator;

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
                        // store item count for watermark tracking
                        if (watermarkStore != null && watermarkKey != null && watermarkExpression == null) {
                            original.setProperty(SPLIT_WATERMARK_COUNT, index);
                        }
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
                        if (part instanceof Message message) {
                            newExchange.setIn(message);
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
            if (pairs instanceof Closeable closeable) {
                IOHelper.close(closeable, "Splitter:ProcessorExchangePairs");
            }
        }

        return result;
    }

    @Override
    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs, boolean hasNext) {
        exchange.setProperty(ExchangePropertyKey.SPLIT_INDEX, index);
        if (allPairs instanceof Collection) {
            // non-streaming mode, so we know the total size already
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

    public int getGroup() {
        return group;
    }

    public void setGroup(int group) {
        this.group = group;
    }

    public double getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(double errorThreshold) {
        this.errorThreshold = errorThreshold;
    }

    public int getMaxFailedRecords() {
        return maxFailedRecords;
    }

    public void setMaxFailedRecords(int maxFailedRecords) {
        this.maxFailedRecords = maxFailedRecords;
    }

    public Map<String, String> getWatermarkStore() {
        return watermarkStore;
    }

    public void setWatermarkStore(Map<String, String> watermarkStore) {
        this.watermarkStore = watermarkStore;
    }

    public String getWatermarkKey() {
        return watermarkKey;
    }

    public void setWatermarkKey(String watermarkKey) {
        this.watermarkKey = watermarkKey;
    }

    public Expression getWatermarkExpression() {
        return watermarkExpression;
    }

    public void setWatermarkExpression(Expression watermarkExpression) {
        this.watermarkExpression = watermarkExpression;
    }

    @Override
    protected boolean shouldContinueOnFailure(Exchange subExchange, Exchange original, int index) {
        SplitFailureTracker tracker = original.getProperty(SPLIT_FAILURE_TRACKER, SplitFailureTracker.class);
        if (tracker == null) {
            return super.shouldContinueOnFailure(subExchange, original, index);
        }

        // record the failure
        tracker.recordFailure(index, subExchange.getException());

        // check if we've exceeded the max failed records
        if (maxFailedRecords > 0 && tracker.getFailureCount() >= maxFailedRecords) {
            return false;
        }
        // check if we've exceeded the error ratio threshold
        if (errorThreshold > 0) {
            double ratio = (double) tracker.getFailureCount() / (index + 1);
            if (ratio >= errorThreshold) {
                return false;
            }
        }

        // continue processing - clear the exception so aggregation proceeds normally
        subExchange.setException(null);
        return true;
    }

    static final class SplitFailureTracker {
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger totalItems = new AtomicInteger();
        private final CopyOnWriteArrayList<SplitFailure> failures = new CopyOnWriteArrayList<>();

        record SplitFailure(int index, Exception exception) {
        }

        void recordFailure(int index, Exception exception) {
            failureCount.incrementAndGet();
            failures.add(new SplitFailure(index, exception));
        }

        void setTotalItems(int count) {
            totalItems.set(count);
        }

        int getTotalItems() {
            return totalItems.get();
        }

        int getFailureCount() {
            return failureCount.get();
        }

        List<SplitFailure> getFailures() {
            return Collections.unmodifiableList(failures);
        }
    }

    private void updateWatermark(Exchange exchange) {
        // don't update watermark if processing was aborted (allows retry)
        if (exchange.getException() != null) {
            return;
        }

        if (watermarkExpression != null) {
            // value-based: evaluate expression on the exchange after split completion
            String newValue = watermarkExpression.evaluate(exchange, String.class);
            if (newValue != null) {
                watermarkStore.put(watermarkKey, newValue);
            }
        } else {
            // index-based: compute absolute last index and store it
            int offset = exchange.getProperty(SPLIT_WATERMARK_OFFSET, 0, Integer.class);
            Integer count = exchange.getProperty(SPLIT_WATERMARK_COUNT, Integer.class);
            if (count != null && count > 0) {
                int lastAbsoluteIndex = offset + count - 1;
                watermarkStore.put(watermarkKey, String.valueOf(lastAbsoluteIndex));
            }
        }

        // clean up internal properties
        exchange.removeProperty(SPLIT_WATERMARK_OFFSET);
        exchange.removeProperty(SPLIT_WATERMARK_COUNT);
    }

    private void buildSplitResult(Exchange exchange) {
        SplitFailureTracker tracker = exchange.getProperty(SPLIT_FAILURE_TRACKER, SplitFailureTracker.class);
        if (tracker == null) {
            return;
        }

        int totalItems = tracker.getTotalItems();
        boolean aborted = exchange.getException() != null;

        List<SplitResult.Failure> failures = new ArrayList<>();
        for (SplitFailureTracker.SplitFailure f : tracker.getFailures()) {
            failures.add(new SplitResult.Failure(f.index(), f.exception()));
        }

        SplitResult result = new SplitResult(totalItems, tracker.getFailureCount(), failures, aborted);
        exchange.setProperty(ExchangePropertyKey.SPLIT_RESULT, result);

        // remove internal tracker
        exchange.removeProperty(SPLIT_FAILURE_TRACKER);
    }

    private Exchange copyAndPrepareSubExchange(Exchange exchange) {
        Exchange answer = processorExchangeFactory.createCopy(exchange);
        // must preserve exchange id
        answer.setExchangeId(exchange.getExchangeId());
        if (exchange.getContext().isMessageHistory()) {
            // we do not want to copy the message history for split sub-messages
            answer.removeProperty(ExchangePropertyKey.MESSAGE_HISTORY);
        }

        if (isParallelProcessing()) {
            //we do not want to copy JPA entityManager (which is not meant for concurrent use) in parallel mode
            //jpa component takes care of the entityManager if property is removed
            answer.removeProperty(Exchange.JPA_ENTITY_MANAGER);
        }
        return answer;
    }
}
