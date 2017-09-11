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
package org.apache.camel.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelUnitOfWorkException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.SubUnitOfWork;
import org.apache.camel.spi.SubUnitOfWorkCallback;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationVetoable;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link org.apache.camel.spi.UnitOfWork}
 */
public class DefaultUnitOfWork implements UnitOfWork, Service {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultUnitOfWork.class);

    // TODO: This implementation seems to have transformed itself into a to broad concern
    // where unit of work is doing a bit more work than the transactional aspect that ties
    // to its name. Maybe this implementation should be named ExchangeContext and we can
    // introduce a simpler UnitOfWork concept. This would also allow us to refactor the
    // SubUnitOfWork into a general parent/child unit of work concept. However this
    // requires API changes and thus is best kept for Camel 3.0

    private UnitOfWork parent;
    private String id;
    private CamelContext context;
    private List<Synchronization> synchronizations;
    private Message originalInMessage;
    private TracedRouteNodes tracedRouteNodes;
    private Set<Object> transactedBy;
    private final Deque<RouteContext> routeContextStack = new ArrayDeque<>();
    private Deque<DefaultSubUnitOfWork> subUnitOfWorks;
    private final transient Logger log;
    
    public DefaultUnitOfWork(Exchange exchange) {
        this(exchange, LOG);
    }

    protected DefaultUnitOfWork(Exchange exchange, Logger logger) {
        log = logger;
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork created for ExchangeId: {} with {}", exchange.getExchangeId(), exchange);
        }

        context = exchange.getContext();

        // only use tracer if explicit enabled
        if (context.isTracing() != null && context.isTracing()) {
            // backwards compatible
            tracedRouteNodes = new DefaultTracedRouteNodes();
        }

        if (context.isAllowUseOriginalMessage()) {
            // special for JmsMessage as it can cause it to loose headers later.
            if (exchange.getIn().getClass().getName().equals("org.apache.camel.component.jms.JmsMessage")) {
                this.originalInMessage = new DefaultMessage(context);
                this.originalInMessage.setBody(exchange.getIn().getBody());
                this.originalInMessage.getHeaders().putAll(exchange.getIn().getHeaders());
            } else {
                this.originalInMessage = exchange.getIn().copy();
            }
            // must preserve exchange on the original in message
            if (this.originalInMessage instanceof MessageSupport) {
                ((MessageSupport) this.originalInMessage).setExchange(exchange);
            }
        }

        // mark the creation time when this Exchange was created
        if (exchange.getProperty(Exchange.CREATED_TIMESTAMP) == null) {
            exchange.setProperty(Exchange.CREATED_TIMESTAMP, new Date());
        }

        // inject breadcrumb header if enabled
        if (exchange.getContext().isUseBreadcrumb()) {
            // create or use existing breadcrumb
            String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
            if (breadcrumbId == null) {
                // no existing breadcrumb, so create a new one based on the exchange id
                breadcrumbId = exchange.getExchangeId();
                exchange.getIn().setHeader(Exchange.BREADCRUMB_ID, breadcrumbId);
            }
        }
        
        // setup whether the exchange is externally redelivered or not (if not initialized before)
        // store as property so we know that the origin exchange was redelivered
        if (exchange.getProperty(Exchange.EXTERNAL_REDELIVERED) == null) {
            Boolean redelivered = exchange.isExternalRedelivered();
            if (redelivered == null) {
                // not from a transactional resource so mark it as false by default
                redelivered = false;
            }
            exchange.setProperty(Exchange.EXTERNAL_REDELIVERED, redelivered);
        }

        // fire event
        try {
            EventHelper.notifyExchangeCreated(exchange.getContext(), exchange);
        } catch (Throwable e) {
            // must catch exceptions to ensure the exchange is not failing due to notification event failed
            log.warn("Exception occurred during event notification. This exception will be ignored.", e);
        }

        // register to inflight registry
        if (exchange.getContext() != null) {
            exchange.getContext().getInflightRepository().add(exchange);
        }
    }

    UnitOfWork newInstance(Exchange exchange) {
        return new DefaultUnitOfWork(exchange);
    }

    @Override
    public void setParentUnitOfWork(UnitOfWork parentUnitOfWork) {
        this.parent = parentUnitOfWork;
    }

    public UnitOfWork createChildUnitOfWork(Exchange childExchange) {
        // create a new child unit of work, and mark me as its parent
        UnitOfWork answer = newInstance(childExchange);
        answer.setParentUnitOfWork(this);
        return answer;
    }

    public void start() throws Exception {
        id = null;
    }

    public void stop() throws Exception {
        // need to clean up when we are stopping to not leak memory
        if (synchronizations != null) {
            synchronizations.clear();
        }
        if (tracedRouteNodes != null) {
            tracedRouteNodes.clear();
        }
        if (transactedBy != null) {
            transactedBy.clear();
        }
        routeContextStack.clear();
        if (subUnitOfWorks != null) {
            subUnitOfWorks.clear();
        }
        originalInMessage = null;
        parent = null;
        id = null;
    }

    public synchronized void addSynchronization(Synchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<Synchronization>();
        }
        log.trace("Adding synchronization {}", synchronization);
        synchronizations.add(synchronization);
    }

    public synchronized void removeSynchronization(Synchronization synchronization) {
        if (synchronizations != null) {
            synchronizations.remove(synchronization);
        }
    }

    public synchronized boolean containsSynchronization(Synchronization synchronization) {
        return synchronizations != null && synchronizations.contains(synchronization);
    }

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
            if (synchronization instanceof SynchronizationVetoable) {
                SynchronizationVetoable veto = (SynchronizationVetoable) synchronization;
                handover = veto.allowHandover();
            }

            if (handover && (filter == null || filter.test(synchronization))) {
                log.trace("Handover synchronization {} to: {}", synchronization, target);
                target.addOnCompletion(synchronization);
                // remove it if its handed over
                it.remove();
            } else {
                log.trace("Handover not allow for synchronization {}", synchronization);
            }
        }
    }

    public void done(Exchange exchange) {
        log.trace("UnitOfWork done for ExchangeId: {} with {}", exchange.getExchangeId(), exchange);

        boolean failed = exchange.isFailed();

        // at first done the synchronizations
        UnitOfWorkHelper.doneSynchronizations(exchange, synchronizations, log);

        // notify uow callback if in use
        try {
            SubUnitOfWorkCallback uowCallback = getSubUnitOfWorkCallback();
            if (uowCallback != null) {
                uowCallback.onDone(exchange);
            }
        } catch (Throwable e) {
            // must catch exceptions to ensure synchronizations is also invoked
            log.warn("Exception occurred during savepoint onDone. This exception will be ignored.", e);
        }

        // unregister from inflight registry, before signalling we are done
        if (exchange.getContext() != null) {
            exchange.getContext().getInflightRepository().remove(exchange);
        }

        // then fire event to signal the exchange is done
        try {
            if (failed) {
                EventHelper.notifyExchangeFailed(exchange.getContext(), exchange);
            } else {
                EventHelper.notifyExchangeDone(exchange.getContext(), exchange);
            }
        } catch (Throwable e) {
            // must catch exceptions to ensure synchronizations is also invoked
            log.warn("Exception occurred during event notification. This exception will be ignored.", e);
        }
    }

    @Override
    public void beforeRoute(Exchange exchange, Route route) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork beforeRoute: {} for ExchangeId: {} with {}", new Object[]{route.getId(), exchange.getExchangeId(), exchange});
        }
        UnitOfWorkHelper.beforeRouteSynchronizations(route, exchange, synchronizations, log);
    }

    @Override
    public void afterRoute(Exchange exchange, Route route) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork afterRoute: {} for ExchangeId: {} with {}", new Object[]{route.getId(), exchange.getExchangeId(), exchange});
        }
        UnitOfWorkHelper.afterRouteSynchronizations(route, exchange, synchronizations, log);
    }

    public String getId() {
        if (id == null) {
            id = context.getUuidGenerator().generateUuid();
        }
        return id;
    }

    public Message getOriginalInMessage() {
        if (originalInMessage == null && !context.isAllowUseOriginalMessage()) {
            throw new IllegalStateException("AllowUseOriginalMessage is disabled. Cannot access the original message.");
        }
        return originalInMessage;
    }

    public TracedRouteNodes getTracedRouteNodes() {
        return tracedRouteNodes;
    }

    public boolean isTransacted() {
        return transactedBy != null && !transactedBy.isEmpty();
    }

    public boolean isTransactedBy(Object key) {
        return getTransactedBy().contains(key);
    }

    public void beginTransactedBy(Object key) {
        getTransactedBy().add(key);
    }

    public void endTransactedBy(Object key) {
        getTransactedBy().remove(key);
    }

    public RouteContext getRouteContext() {
        return routeContextStack.peek();
    }

    public void pushRouteContext(RouteContext routeContext) {
        routeContextStack.push(routeContext);
    }

    public RouteContext popRouteContext() {
        try {
            return routeContextStack.pop();
        } catch (NoSuchElementException e) {
            // ignore and return null
        }
        return null;
    }

    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        // no wrapping needed
        return callback;
    }

    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
    }

    @Override
    public void beginSubUnitOfWork(Exchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("beginSubUnitOfWork exchangeId: {}", exchange.getExchangeId());
        }

        if (subUnitOfWorks == null) {
            subUnitOfWorks = new ArrayDeque<>();
        }
        subUnitOfWorks.push(new DefaultSubUnitOfWork());
    }

    @Override
    public void endSubUnitOfWork(Exchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("endSubUnitOfWork exchangeId: {}", exchange.getExchangeId());
        }

        if (subUnitOfWorks == null || subUnitOfWorks.isEmpty()) {
            return;
        }

        // pop last sub unit of work as its now ended
        SubUnitOfWork subUoW = null;
        try {
            subUoW = subUnitOfWorks.pop();
        } catch (NoSuchElementException e) {
            // ignore
        }
        if (subUoW != null && subUoW.isFailed()) {
            // the sub unit of work failed so set an exception containing all the caused exceptions
            // and mark the exchange for rollback only

            // if there are multiple exceptions then wrap those into another exception with them all
            Exception cause;
            List<Exception> list = subUoW.getExceptions();
            if (list != null) {
                if (list.size() == 1) {
                    cause = list.get(0);
                } else {
                    cause = new CamelUnitOfWorkException(exchange, list);
                }
                exchange.setException(cause);
            }
            // mark it as rollback and that the unit of work is exhausted. This ensures that we do not try
            // to redeliver this exception (again)
            exchange.setProperty(Exchange.ROLLBACK_ONLY, true);
            exchange.setProperty(Exchange.UNIT_OF_WORK_EXHAUSTED, true);
            // and remove any indications of error handled which will prevent this exception to be noticed
            // by the error handler which we want to react with the result of the sub unit of work
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, null);
            exchange.setProperty(Exchange.FAILURE_HANDLED, null);
            if (log.isTraceEnabled()) {
                log.trace("endSubUnitOfWork exchangeId: {} with {} caused exceptions.", exchange.getExchangeId(), list != null ? list.size() : 0);
            }
        }
    }

    @Override
    public SubUnitOfWorkCallback getSubUnitOfWorkCallback() {
        // if there is a parent-child relationship between unit of works
        // then we should use the callback strategies from the parent
        if (parent != null) {
            return parent.getSubUnitOfWorkCallback();
        }

        return subUnitOfWorks != null ? subUnitOfWorks.peek() : null;
    }

    private Set<Object> getTransactedBy() {
        if (transactedBy == null) {
            transactedBy = new LinkedHashSet<Object>();
        }
        return transactedBy;
    }

    @Override
    public String toString() {
        return "DefaultUnitOfWork";
    }
}
