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
package org.apache.camel.impl.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PooledExchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationVetoable;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link org.apache.camel.spi.UnitOfWork}
 */
public class DefaultUnitOfWork implements UnitOfWork {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultUnitOfWork.class);

    // instances used by MDCUnitOfWork
    final InflightRepository inflightRepository;
    final StreamCachingStrategy streamCachingStrategy;
    final boolean allowUseOriginalMessage;
    final boolean useBreadcrumb;

    private final CamelContext context;
    private final Deque<Route> routes = new ArrayDeque<>(8);
    private final Logger log;
    private Exchange exchange;
    private List<Synchronization> synchronizations;
    private Message originalInMessage;
    private Set<Object> transactedBy;

    public DefaultUnitOfWork(Exchange exchange) {
        this(exchange, exchange.getContext().getInflightRepository(), exchange.getContext().isAllowUseOriginalMessage(),
             exchange.getContext().isUseBreadcrumb());
    }

    protected DefaultUnitOfWork(Exchange exchange, Logger logger, InflightRepository inflightRepository,
                                boolean allowUseOriginalMessage, boolean useBreadcrumb) {
        this.allowUseOriginalMessage = allowUseOriginalMessage;
        this.useBreadcrumb = useBreadcrumb;
        this.context = exchange.getContext();
        this.inflightRepository = inflightRepository;
        this.streamCachingStrategy = exchange.getContext().getStreamCachingStrategy();
        this.log = logger;

        doOnPrepare(exchange);
    }

    public DefaultUnitOfWork(Exchange exchange, InflightRepository inflightRepository, boolean allowUseOriginalMessage,
                             boolean useBreadcrumb) {
        this(exchange, LOG, inflightRepository, allowUseOriginalMessage, useBreadcrumb);

    }

    UnitOfWork newInstance(Exchange exchange) {
        return new DefaultUnitOfWork(exchange, inflightRepository, allowUseOriginalMessage, useBreadcrumb);
    }

    @Override
    public boolean onPrepare(Exchange exchange) {
        if (this.exchange == null) {
            doOnPrepare(exchange);
            return true;
        } else {
            return false;
        }
    }

    private boolean isStreamCacheInUse(Exchange exchange) {
        boolean inUse = streamCachingStrategy.isEnabled();
        if (inUse) {
            // the original route (from route) may have disabled stream caching
            String rid = exchange.getFromRouteId();
            if (rid != null) {
                Route route = exchange.getContext().getRoute(rid);
                if (route != null) {
                    inUse = route.isStreamCaching() != null && route.isStreamCaching();
                }
            }
        }
        return inUse;
    }

    private void doOnPrepare(Exchange exchange) {
        // unit of work is reused, so setup for this exchange
        this.exchange = exchange;

        if (allowUseOriginalMessage) {
            this.originalInMessage = exchange.getIn().copy();
            if (isStreamCacheInUse(exchange)) {
                // if the input body is streaming we need to cache it, so we can access the original input message (like stream caching advice does)
                StreamCache cache
                        = StreamCachingHelper.convertToStreamCache(streamCachingStrategy, exchange, this.originalInMessage);
                if (cache != null) {
                    this.originalInMessage.setBody(cache);
                    // replace original incoming message with stream cache
                    this.exchange.getIn().setBody(cache);
                }
            }
        }

        // inject breadcrumb header if enabled
        if (useBreadcrumb) {
            // create or use existing breadcrumb
            String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
            if (breadcrumbId == null) {
                // no existing breadcrumb, so create a new one based on the exchange id
                breadcrumbId = exchange.getExchangeId();
                exchange.getIn().setHeader(Exchange.BREADCRUMB_ID, breadcrumbId);
            }
        }

        // fire event
        if (context.getCamelContextExtension().isEventNotificationApplicable()) {
            try {
                EventHelper.notifyExchangeCreated(context, exchange);
            } catch (Exception e) {
                // must catch exceptions to ensure the exchange is not failing due to notification event failed
                log.warn("Exception occurred during event notification. This exception will be ignored.", e);
            }
        }

        // register to inflight registry
        inflightRepository.add(exchange);
    }

    @Override
    public void reset() {
        this.exchange = null;
        routes.clear();
        if (synchronizations != null) {
            synchronizations.clear();
        }
        originalInMessage = null;
        if (transactedBy != null) {
            transactedBy.clear();
        }
    }

    @Override
    public void setParentUnitOfWork(UnitOfWork parentUnitOfWork) {
    }

    @Override
    public UnitOfWork createChildUnitOfWork(Exchange childExchange) {
        // create a new child unit of work, and mark me as its parent
        UnitOfWork answer = newInstance(childExchange);
        answer.setParentUnitOfWork(this);
        return answer;
    }

    @Override
    public synchronized void addSynchronization(Synchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<>(8);
        }
        log.trace("Adding synchronization {}", synchronization);
        synchronizations.add(synchronization);
    }

    @Override
    public synchronized void removeSynchronization(Synchronization synchronization) {
        if (synchronizations != null) {
            synchronizations.remove(synchronization);
        }
    }

    @Override
    public synchronized boolean containsSynchronization(Synchronization synchronization) {
        return synchronizations != null && synchronizations.contains(synchronization);
    }

    @Override
    public void handoverSynchronization(Exchange target) {
        handoverSynchronization(target, null);
    }

    @Override
    public void handoverSynchronization(Exchange target, Predicate<Synchronization> filter) {
        if (synchronizations == null || synchronizations.isEmpty()) {
            return;
        }

        Iterator<Synchronization> it = synchronizations.iterator();
        while (it.hasNext()) {
            Synchronization synchronization = it.next();

            boolean handover = true;
            SynchronizationVetoable veto = null;
            if (synchronization instanceof SynchronizationVetoable) {
                veto = (SynchronizationVetoable) synchronization;
                handover = veto.allowHandover();
            }

            if (handover && (filter == null || filter.test(synchronization))) {
                log.trace("Handover synchronization {} to: {}", synchronization, target);
                target.getExchangeExtension().addOnCompletion(synchronization);
                // Allow the synchronization to do housekeeping before transfer
                if (veto != null) {
                    veto.beforeHandover(target);
                }
                // remove it if its handed over
                it.remove();
            } else {
                log.trace("Handover not allow for synchronization {}", synchronization);
            }
        }
    }

    @Override
    public void done(Exchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork done for ExchangeId: {} with {}", exchange.getExchangeId(), exchange);
        }

        // at first done the synchronizations
        UnitOfWorkHelper.doneSynchronizations(exchange, synchronizations);

        // unregister from inflight registry, before signalling we are done
        inflightRepository.remove(exchange);

        if (context.getCamelContextExtension().isEventNotificationApplicable()) {
            // then fire event to signal the exchange is done
            try {
                final boolean failed = exchange.isFailed();
                if (failed) {
                    EventHelper.notifyExchangeFailed(exchange.getContext(), exchange);
                } else {
                    EventHelper.notifyExchangeDone(exchange.getContext(), exchange);
                }
            } catch (Exception e) {
                // must catch exceptions to ensure synchronizations is also invoked
                log.warn("Exception occurred during event notification. This exception will be ignored.", e);
            }
        }

        // the exchange is now done
        if (exchange instanceof PooledExchange) {
            // pooled exchange has its own done logic which will reset this uow for reuse
            // so do not call onDone
            try {
                PooledExchange pooled = (PooledExchange) exchange;
                // only trigger done if we should auto-release
                if (pooled.isAutoRelease()) {
                    ((PooledExchange) exchange).done();
                }
            } catch (Exception e) {
                // must catch exceptions to ensure synchronizations is also invoked
                log.warn("Exception occurred during exchange done. This exception will be ignored.", e);
            }
        } else {
            onDone();
        }
    }

    protected void onDone() {
        // MUST clear and set uow to null on exchange after done
        // in case the same exchange is manually reused by Camel end users (should happen seldom)
        exchange.getExchangeExtension().setUnitOfWork(null);
    }

    @Override
    public void beforeRoute(Exchange exchange, Route route) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork beforeRoute: {} for ExchangeId: {} with {}", route.getId(), exchange.getExchangeId(),
                    exchange);
        }
        if (synchronizations != null && !synchronizations.isEmpty()) {
            UnitOfWorkHelper.beforeRouteSynchronizations(route, exchange, synchronizations, log);
        }
    }

    @Override
    public void afterRoute(Exchange exchange, Route route) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork afterRoute: {} for ExchangeId: {} with {}", route.getId(), exchange.getExchangeId(),
                    exchange);
        }
        if (synchronizations != null && !synchronizations.isEmpty()) {
            UnitOfWorkHelper.afterRouteSynchronizations(route, exchange, synchronizations, log);
        }
    }

    @Override
    public Message getOriginalInMessage() {
        if (originalInMessage == null && !context.isAllowUseOriginalMessage()) {
            throw new IllegalStateException("AllowUseOriginalMessage is disabled. Cannot access the original message.");
        }
        return originalInMessage;
    }

    @Override
    public boolean isTransacted() {
        return transactedBy != null && !transactedBy.isEmpty();
    }

    @Override
    public boolean isTransactedBy(Object key) {
        return transactedBy != null && getTransactedBy().contains(key);
    }

    @Override
    public void beginTransactedBy(Object key) {
        exchange.getExchangeExtension().setTransacted(true);
        getTransactedBy().add(key);
    }

    @Override
    public void endTransactedBy(Object key) {
        getTransactedBy().remove(key);
        // we may still be transacted even if we end this section of transaction
        boolean transacted = isTransacted();
        exchange.getExchangeExtension().setTransacted(transacted);
    }

    @Override
    public Route getRoute() {
        return routes.peek();
    }

    @Override
    public void pushRoute(Route route) {
        routes.push(route);
    }

    @Override
    public Route popRoute() {
        return routes.poll();
    }

    @Override
    public int routeStackLevel() {
        return routes.size();
    }

    @Override
    public boolean isBeforeAfterProcess() {
        return false;
    }

    @Override
    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        // CAMEL-18255: support running afterProcess from the async callback
        return isBeforeAfterProcess() ? new UnitOfWorkCallback(callback, processor) : callback;
    }

    @Override
    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        // noop
    }

    private Set<Object> getTransactedBy() {
        if (transactedBy == null) {
            // no need to take up so much space so use a lille set
            transactedBy = new HashSet<>(4);
        }
        return transactedBy;
    }

    @Override
    public String toString() {
        return "DefaultUnitOfWork";
    }

    private final class UnitOfWorkCallback implements AsyncCallback {

        private final AsyncCallback delegate;
        private final Processor processor;

        private UnitOfWorkCallback(AsyncCallback delegate, Processor processor) {
            this.delegate = delegate;
            this.processor = processor;
        }

        @Override
        public void done(boolean doneSync) {
            delegate.done(doneSync);
            afterProcess(processor, exchange, delegate, doneSync);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
