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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Implements a dynamic <a
 * href="http://camel.apache.org/recipient-list.html">Recipient List</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on some dynamic expression.
 *
 * @version $Revision$
 */
public class RecipientList extends ServiceSupport implements Processor {
    private ProducerCache producerCache;
    private Expression expression;
    private final String delimiter;
    private boolean parallelProcessing;
    private boolean stopOnException;
    private ExecutorService executorService;
    private AggregationStrategy aggregationStrategy = new UseLatestAggregationStrategy();

    public RecipientList() {
        // use comma by default as delimiter
        this.delimiter = ",";
    }

    public RecipientList(String delimiter) {
        this.delimiter = delimiter;
    }

    public RecipientList(Expression expression) {
        // use comma by default as delimiter
        this(expression, ",");
    }

    public RecipientList(Expression expression, String delimiter) {
        ObjectHelper.notNull(expression, "expression");
        ObjectHelper.notEmpty(delimiter, "delimiter");
        this.expression = expression;
        this.delimiter = delimiter;
    }

    @Override
    public String toString() {
        return "RecipientList[" + (expression != null ? expression : "") + "]";
    }

    public void process(Exchange exchange) throws Exception {
        Object receipientList = expression.evaluate(exchange, Object.class);
        sendToRecipientList(exchange, receipientList);
    }

    /**
     * Sends the given exchange to the recipient list
     */
    public void sendToRecipientList(Exchange exchange, Object receipientList) throws Exception {
        Iterator<Object> iter = ObjectHelper.createIterator(receipientList, delimiter);

        // we should acquire and release the producers we need so we can leverage the producer
        // cache to the fullest
        ProducerCache cache = getProducerCache(exchange);
        Map<Endpoint, Producer> producers = new LinkedHashMap<Endpoint, Producer>();
        try {
            List<Processor> processors = new ArrayList<Processor>();
            while (iter.hasNext()) {
                Object recipient = iter.next();
                Endpoint endpoint = resolveEndpoint(exchange, recipient);
                // acquire producer which we then release later
                Producer producer = cache.acquireProducer(endpoint);
                processors.add(producer);
                producers.put(endpoint, producer);
            }

            MulticastProcessor mp = new MulticastProcessor(exchange.getContext(), processors, getAggregationStrategy(),
                                                           isParallelProcessing(), getExecutorService(), false, isStopOnException());

            // now let the multicast process the exchange
            mp.process(exchange);
        } finally {
            // and release the producers back to the producer cache
            for (Map.Entry<Endpoint, Producer> entry : producers.entrySet()) {
                cache.releaseProducer(entry.getKey(), entry.getValue());
            }
        }
    }

    protected ProducerCache getProducerCache(Exchange exchange) throws Exception {
        // setup producer cache as we need to use the pluggable service pool defined on camel context
        if (producerCache == null) {
            this.producerCache = new ProducerCache(exchange.getContext());
            this.producerCache.start();
        }
        return this.producerCache;
    }

    protected Endpoint resolveEndpoint(Exchange exchange, Object recipient) {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            recipient = ((String)recipient).trim();
        }
        return ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected void doStart() throws Exception {
        if (producerCache != null) {
            producerCache.start();
        }
    }

    protected void doStop() throws Exception {
        if (producerCache != null) {
            producerCache.stop();
        }
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public boolean isStopOnException() {
        return stopOnException;
    }

    public void setStopOnException(boolean stopOnException) {
        this.stopOnException = stopOnException;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }
}
