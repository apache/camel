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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.spi.ClaimCheckRepository;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClaimCheck EIP implementation.
 * <p/>
 * The current Claim Check EIP implementation in Camel is only intended for temporary memory repository. Likewise the
 * repository is not shared among {@link Exchange}s, but a private instance is created per {@link Exchange}. This guards
 * against concurrent and thread-safe issues. For off-memory persistent storage of data, then use any of the many Camel
 * components that support persistent storage, and do not use this Claim Check EIP implementation.
 */
public class ClaimCheckProcessor extends AsyncProcessorSupport implements IdAware, RouteIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimCheckProcessor.class);

    private CamelContext camelContext;
    private String id;
    private String routeId;
    private String operation;
    private AggregationStrategy aggregationStrategy;
    private String key;
    private Expression keyExpression;
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

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
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

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // the repository is scoped per exchange
        ClaimCheckRepository repo
                = exchange.getProperty(ExchangePropertyKey.CLAIM_CHECK_REPOSITORY, ClaimCheckRepository.class);
        if (repo == null) {
            repo = new DefaultClaimCheckRepository();
            exchange.setProperty(ExchangePropertyKey.CLAIM_CHECK_REPOSITORY, repo);
        }

        try {
            String claimKey = keyExpression.evaluate(exchange, String.class);

            if ("Set".equals(operation)) {
                // copy exchange, and do not share the unit of work
                Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
                boolean addedNew = repo.add(claimKey, copy);
                if (addedNew) {
                    LOG.debug("Add: {} -> {}", claimKey, copy);
                } else {
                    LOG.debug("Override: {} -> {}", claimKey, copy);
                }
            } else if ("Get".equals(operation)) {
                Exchange copy = repo.get(claimKey);
                LOG.debug("Get: {} -> {}", claimKey, exchange);
                if (copy != null) {
                    Exchange result = aggregationStrategy.aggregate(exchange, copy);
                    if (result != null) {
                        ExchangeHelper.copyResultsPreservePattern(exchange, result);
                    }
                }
            } else if ("GetAndRemove".equals(operation)) {
                Exchange copy = repo.getAndRemove(claimKey);
                LOG.debug("GetAndRemove: {} -> {}", claimKey, exchange);
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
                LOG.debug("Push: {} -> {}", claimKey, copy);
                repo.push(copy);
            } else if ("Pop".equals(operation)) {
                Exchange copy = repo.pop();
                LOG.debug("Pop: {} -> {}", claimKey, exchange);
                if (copy != null) {
                    // prepare the exchanges for aggregation
                    ExchangeHelper.prepareAggregation(exchange, copy);
                    Exchange result = aggregationStrategy.aggregate(exchange, copy);
                    if (result != null) {
                        ExchangeHelper.copyResultsPreservePattern(exchange, result);
                    }
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    protected void doInit() throws Exception {
        if (aggregationStrategy == null) {
            aggregationStrategy = createAggregationStrategy();
        }
        CamelContextAware.trySetCamelContext(aggregationStrategy, camelContext);

        if (LanguageSupport.hasSimpleFunction(key)) {
            keyExpression = camelContext.resolveLanguage("simple").createExpression(key);
        } else {
            keyExpression = camelContext.resolveLanguage("constant").createExpression(key);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(operation, "operation", this);
        ServiceHelper.startService(aggregationStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(aggregationStrategy);
    }

    @Override
    public String toString() {
        return id;
    }

    protected AggregationStrategy createAggregationStrategy() {
        ClaimCheckAggregationStrategy answer = new ClaimCheckAggregationStrategy();
        answer.setFilter(filter);
        return answer;
    }
}
