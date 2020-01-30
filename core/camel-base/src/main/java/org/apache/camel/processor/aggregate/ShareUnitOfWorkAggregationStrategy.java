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
package org.apache.camel.processor.aggregate;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * An {@link AggregationStrategy} which are used when the option <tt>shareUnitOfWork</tt> is enabled
 * on EIPs such as multicast, splitter or recipientList.
 * <p/>
 * This strategy wraps the actual in use strategy to provide the logic needed for making shareUnitOfWork work.
 * <p/>
 * This strategy is <b>not</b> intended for end users to use.
 */
public final class ShareUnitOfWorkAggregationStrategy extends ServiceSupport implements AggregationStrategy, CamelContextAware {

    private final AggregationStrategy strategy;
    private CamelContext camelContext;

    public ShareUnitOfWorkAggregationStrategy(AggregationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(camelContext);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public AggregationStrategy getDelegate() {
        return strategy;
    }

    @Override
    public boolean canPreComplete() {
        return strategy.canPreComplete();
    }

    @Override
    public boolean preComplete(Exchange oldExchange, Exchange newExchange) {
        return strategy.preComplete(oldExchange, newExchange);
    }

    @Override
    public void onCompletion(Exchange exchange) {
        strategy.onCompletion(exchange);
    }

    @Override
    public void timeout(Exchange exchange, int index, int total, long timeout) {
        strategy.timeout(exchange, index, total, timeout);
    }

    @Override
    public void onOptimisticLockFailure(Exchange oldExchange, Exchange newExchange) {
        strategy.onOptimisticLockFailure(oldExchange, newExchange);
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // aggregate using the actual strategy first
        Exchange answer = strategy.aggregate(oldExchange, newExchange);
        // ensure any errors is propagated from the new exchange to the answer
        propagateFailure(answer, newExchange);

        return answer;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange, Exchange inputExchange) {
        // aggregate using the actual strategy first
        Exchange answer = strategy.aggregate(oldExchange, newExchange, inputExchange);
        // ensure any errors is propagated from the new exchange to the answer
        propagateFailure(answer, newExchange);

        return answer;
    }

    protected void propagateFailure(Exchange answer, Exchange newExchange) {
        ExtendedExchange nee = (ExtendedExchange) newExchange;
        // if new exchange failed then propagate all the error related properties to the answer
        if (nee.isFailed() || nee.isRollbackOnly() || nee.isRollbackOnlyLast() || nee.isErrorHandlerHandled()) {
            if (newExchange.getException() != null) {
                answer.setException(newExchange.getException());
            }
            if (newExchange.getProperty(Exchange.EXCEPTION_CAUGHT) != null) {
                answer.setProperty(Exchange.EXCEPTION_CAUGHT, newExchange.getProperty(Exchange.EXCEPTION_CAUGHT));
            }
            if (newExchange.getProperty(Exchange.FAILURE_ENDPOINT) != null) {
                answer.setProperty(Exchange.FAILURE_ENDPOINT, newExchange.getProperty(Exchange.FAILURE_ENDPOINT));
            }
            if (newExchange.getProperty(Exchange.FAILURE_ROUTE_ID) != null) {
                answer.setProperty(Exchange.FAILURE_ROUTE_ID, newExchange.getProperty(Exchange.FAILURE_ROUTE_ID));
            }
            if (newExchange.adapt(ExtendedExchange.class).getErrorHandlerHandled() != null) {
                answer.adapt(ExtendedExchange.class).setErrorHandlerHandled(newExchange.adapt(ExtendedExchange.class).getErrorHandlerHandled());
            }
            if (newExchange.getProperty(Exchange.FAILURE_HANDLED) != null) {
                answer.setProperty(Exchange.FAILURE_HANDLED, newExchange.getProperty(Exchange.FAILURE_HANDLED));
            }
        }
    }

    @Override
    public String toString() {
        return "ShareUnitOfWorkAggregationStrategy";
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(strategy);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownServices(strategy);
    }
}
