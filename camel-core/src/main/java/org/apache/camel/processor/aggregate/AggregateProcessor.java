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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.DefaultTimeoutMap;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.TimeoutMap;
import org.apache.camel.util.TimeoutMapEntry;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <a href="http://camel.apache.org/aggregator.html">Aggregator</a> EIP pattern.
 *
 * @version $Revision$
 */
public class AggregateProcessor extends ServiceSupport implements Processor, Navigate<Processor> {

    private static final Log LOG = LogFactory.getLog(AggregateProcessor.class);

    private TimeoutMap<Object, Exchange> timeoutMap;
    private final Processor processor;
    private final AggregationStrategy aggregationStrategy;
    private final Expression correlationExpression;
    private ExecutorService executorService;
    private AggregationRepository<Object> aggregationRepository = new MemoryAggregationRepository();

    // different ways to have completion triggered
    private boolean eagerEvaluateCompletionPredicate;
    private Predicate completionPredicate;
    private long completionTimeout;
    private int completionAggregatedSize;

    public AggregateProcessor(Processor processor, Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        ObjectHelper.notNull(processor, "processor");
        ObjectHelper.notNull(correlationExpression, "correlationExpression");
        ObjectHelper.notNull(aggregationStrategy, "aggregationStrategy");
        this.processor = processor;
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
    }

    @Override
    public String toString() {
        return "AggregateProcessor[to: " + processor + "]";
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(processor);
        return answer;
    }

    public boolean hasNext() {
        return processor != null;
    }

    public void process(Exchange exchange) throws Exception {
        // compute correlation expression
        Object key = correlationExpression.evaluate(exchange, Object.class);
        if (ObjectHelper.isEmpty(key)) {
            throw new CamelExchangeException("Correlation key could not be evaluated to a value", exchange);
        }

        Exchange oldExchange = aggregationRepository.get(key);
        Exchange newExchange = exchange;

        Integer size = 1;
        if (oldExchange != null) {
            size = oldExchange.getProperty(Exchange.AGGREGATED_SIZE, Integer.class);
            ObjectHelper.notNull(size, Exchange.AGGREGATED_SIZE + " on " + oldExchange);
            size++;
        }

        // are we complete?
        boolean complete = false;
        if (isEagerEvaluateCompletionPredicate()) {
            complete = isCompleted(key, exchange, size);
        }

        // prepare the exchanges for aggregation and aggregate it
        ExchangeHelper.prepareAggregation(oldExchange, newExchange);
        newExchange = onAggregation(oldExchange, newExchange);
        newExchange.setProperty(Exchange.AGGREGATED_SIZE, size);

        // if not set to evaluate eager then do that after the aggregation
        if (!isEagerEvaluateCompletionPredicate()) {
            complete = isCompleted(key, newExchange, size);
        }

        // only need to update aggregation repository if we are not complete
        if (!complete && !newExchange.equals(oldExchange)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Put exchange:" + newExchange + " with correlation key:"  + key);
            }
            aggregationRepository.add(key, newExchange);
        }

        if (complete) {
            onCompletion(key, newExchange);
        }
    }

    protected Exchange onAggregation(Exchange oldExchange, Exchange newExchange) {
        return aggregationStrategy.aggregate(oldExchange, newExchange);
    }

    protected boolean isCompleted(Object key, Exchange exchange, int size) {
        if (getCompletionPredicate() != null) {
            boolean answer = getCompletionPredicate().matches(exchange);
            if (answer) {
                return true;
            }
        }

        if (getCompletionAggregatedSize() > 0) {
            if (size >= getCompletionAggregatedSize()) {
                return true;
            }
        }

        if (getCompletionTimeout() > 0) {
            // timeout is used so use the timeout map to keep an eye on this
            if (LOG.isDebugEnabled()) {
                LOG.debug("Updating correlation key " + key + " to timeout after " + getCompletionTimeout() + " ms.");
            }
            timeoutMap.put(key, exchange, getCompletionTimeout());
        }

        return false;
    }

    protected void onCompletion(Object key, final Exchange exchange) {
        // remove from repository and timeout map as its completed
        aggregationRepository.remove(key);
        if (timeoutMap != null) {
            timeoutMap.remove(key);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Aggregation complete for correlation key " + key + " sending aggregated exchange: " + exchange);
        }

        // send this exchange
        executorService.submit(new Runnable() {
            public void run() {
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }
            }
        });
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public Predicate getCompletionPredicate() {
        return completionPredicate;
    }

    public void setCompletionPredicate(Predicate completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public boolean isEagerEvaluateCompletionPredicate() {
        return eagerEvaluateCompletionPredicate;
    }

    public void setEagerEvaluateCompletionPredicate(boolean eagerEvaluateCompletionPredicate) {
        this.eagerEvaluateCompletionPredicate = eagerEvaluateCompletionPredicate;
    }

    public long getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(long completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public int getCompletionAggregatedSize() {
        return completionAggregatedSize;
    }

    public void setCompletionAggregatedSize(int completionAggregatedSize) {
        this.completionAggregatedSize = completionAggregatedSize;
    }

    /**
     * Background tasks that looks for aggregated exchanges which is triggered by completion timeouts.
     */
    private class TimeoutReaper extends DefaultTimeoutMap<Object, Exchange> {

        private TimeoutReaper(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
            super(executor, requestMapPollTimeMillis);
        }

        protected boolean isValidForEviction(TimeoutMapEntry<Object, Exchange> entry) {
            if (log.isDebugEnabled()) {
                log.debug("Completion timeout triggered for correlation key: " + entry.getKey());
            }
            onCompletion(entry.getKey(), entry.getValue());
            return true;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (getCompletionTimeout() <= 0 && getCompletionAggregatedSize() <= 0 && getCompletionPredicate() == null) {
            throw new IllegalStateException("At least one of the completions options"
                    + " [completionTimeout, completionAggregatedSize, completionPredicate] must be set");
        }

        ServiceHelper.startService(aggregationRepository);

        if (executorService == null) {
            executorService = ExecutorServiceHelper.newFixedThreadPool(10, "AggregateProcessor", true);
        }

        // start timeout service if its in use
        if (getCompletionTimeout() > 0) {
            ScheduledExecutorService scheduler = ExecutorServiceHelper.newScheduledThreadPool(1, "AggregationProcessorTimeoutReaper", true);
            timeoutMap = new TimeoutReaper(scheduler, 1000L);
            ServiceHelper.startService(timeoutMap);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(timeoutMap);

        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        ServiceHelper.stopService(aggregationRepository);
    }

}
