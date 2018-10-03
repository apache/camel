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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultClaimCheckRepository;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.ClaimCheckRepository;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ServiceHelper;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * ClaimCheck EIP implementation.
 * <p/>
 * The current Claim Check EIP implementation in Camel is only intended for temporary memory repository. Likewise
 * the repository is not shared among {@link Exchange}s, but a private instance is created per {@link Exchange}.
 * This guards against concurrent and thread-safe issues. For off-memory persistent storage of data, then use
 * any of the many Camel components that support persistent storage, and do not use this Claim Check EIP implementation.
 */
public class ClaimCheckProcessor extends ServiceSupport implements AsyncProcessor, IdAware, CamelContextAware {

    private CamelContext camelContext;
    private String id;
    private String operation;
    private AggregationStrategy aggregationStrategy;
    private String key;
    private String filter;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // the repository is scoped per exchange
        ClaimCheckRepository repo = exchange.getProperty(Exchange.CLAIM_CHECK_REPOSITORY, ClaimCheckRepository.class);
        if (repo == null) {
            repo = new DefaultClaimCheckRepository();
            exchange.setProperty(Exchange.CLAIM_CHECK_REPOSITORY, repo);
        }

        try {
            if ("Set".equals(operation)) {
                // copy exchange, and do not share the unit of work
                Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
                boolean addedNew = repo.add(key, copy);
                if (addedNew) {
                    log.debug("Add: {} -> {}", key, copy);
                } else {
                    log.debug("Override: {} -> {}", key, copy);
                }
            } else if ("Get".equals(operation)) {
                Exchange copy = repo.get(key);
                log.debug("Get: {} -> {}", key, exchange);
                if (copy != null) {
                    Exchange result = aggregationStrategy.aggregate(exchange, copy);
                    if (result != null) {
                        ExchangeHelper.copyResultsPreservePattern(exchange, result);
                    }
                }
            } else if ("GetAndRemove".equals(operation)) {
                Exchange copy = repo.getAndRemove(key);
                log.debug("GetAndRemove: {} -> {}", key, exchange);
                if (copy != null) {
                    // prepare the exchanges for aggregation
                    ExchangeHelper.prepareAggregation(exchange, copy);
                    Exchange result = aggregationStrategy.aggregate(exchange, copy);
                    if (result != null) {
                        ExchangeHelper.copyResultsPreservePattern(exchange, result);
                    }
                }
            } else if ("Push".equals(operation)) {
                // copy exchange, and do not share the unit of work
                Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
                log.debug("Push: {} -> {}", key, copy);
                repo.push(copy);
            } else if ("Pop".equals(operation)) {
                Exchange copy = repo.pop();
                log.debug("Pop: {} -> {}", key, exchange);
                if (copy != null) {
                    // prepare the exchanges for aggregation
                    ExchangeHelper.prepareAggregation(exchange, copy);
                    Exchange result = aggregationStrategy.aggregate(exchange, copy);
                    if (result != null) {
                        ExchangeHelper.copyResultsPreservePattern(exchange, result);
                    }
                }
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(operation, "operation", this);

        if (aggregationStrategy == null) {
            aggregationStrategy = createAggregationStrategy();
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }

        ServiceHelper.startService(aggregationStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(aggregationStrategy);
    }

    @Override
    public String toString() {
        return "ClaimCheck[" + operation + "]";
    }

    protected AggregationStrategy createAggregationStrategy() {
        ClaimCheckAggregationStrategy answer = new ClaimCheckAggregationStrategy();
        answer.setFilter(filter);
        return answer;
    }
}
