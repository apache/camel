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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.spi.RouteContext;
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
 *
 * @version 
 */
public class DefaultUnitOfWork implements UnitOfWork, Service {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    private String id;
    private CamelContext context;
    private List<Synchronization> synchronizations;
    private Message originalInMessage;
    private final TracedRouteNodes tracedRouteNodes;
    private Set<Object> transactedBy;
    private final Stack<RouteContext> routeContextStack = new Stack<RouteContext>();

    public DefaultUnitOfWork(Exchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork created for ExchangeId: " + exchange.getExchangeId() + " with " + exchange);
        }
        tracedRouteNodes = new DefaultTracedRouteNodes();
        context = exchange.getContext();

        // TODO: the copy on facade strategy will help us here in the future
        // TODO: optimize to only copy original message if enabled to do so in the route
        // special for JmsMessage as it can cause it to loose headers later.
        // This will be resolved when we get the message facade with copy on write implemented
        if (exchange.getIn().getClass().getSimpleName().equals("JmsMessage")) {
            this.originalInMessage = new DefaultMessage();
            this.originalInMessage.setBody(exchange.getIn().getBody());
            // cannot copy headers with a JmsMessage as the underlying javax.jms.Message object goes nuts 
        } else {
            this.originalInMessage = exchange.getIn().copy();
        }

        // mark the creation time when this Exchange was created
        if (exchange.getProperty(Exchange.CREATED_TIMESTAMP) == null) {
            exchange.setProperty(Exchange.CREATED_TIMESTAMP, new Date());
        }

        // fire event
        EventHelper.notifyExchangeCreated(exchange.getContext(), exchange);

        // register to inflight registry
        if (exchange.getContext() != null) {
            exchange.getContext().getInflightRepository().add(exchange);
        }
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
        originalInMessage = null;

        if (!routeContextStack.isEmpty()) {
            routeContextStack.clear();
        }
    }

    public synchronized void addSynchronization(Synchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<Synchronization>();
        }
        if (log.isTraceEnabled()) {
            log.trace("Adding synchronization " + synchronization);
        }
        synchronizations.add(synchronization);
    }

    public synchronized void removeSynchronization(Synchronization synchronization) {
        if (synchronizations != null) {
            synchronizations.remove(synchronization);
        }
    }

    public void handoverSynchronization(Exchange target) {
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

            if (handover) {
                if (log.isTraceEnabled()) {
                    log.trace("Handover synchronization " + synchronization + " to: " + target);
                }
                target.addOnCompletion(synchronization);
                // remove it if its handed over
                it.remove();
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Handover not allow for synchronization " + synchronization);
                }
            }
        }
    }
   
    public void done(Exchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("UnitOfWork done for ExchangeId: " + exchange.getExchangeId() + " with " + exchange);
        }

        boolean failed = exchange.isFailed();

        // at first done the synchronizations
        UnitOfWorkHelper.doneSynchronizations(exchange, synchronizations, log);

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
        } finally {
            // unregister from inflight registry
            if (exchange.getContext() != null) {
                exchange.getContext().getInflightRepository().remove(exchange);
            }
        }
    }

    public String getId() {
        if (id == null) {
            id = context.getUuidGenerator().generateUuid();
        }
        return id;
    }

    public Message getOriginalInMessage() {
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
        if (routeContextStack.isEmpty()) {
            return null;
        }
        return routeContextStack.peek();
    }

    public void pushRouteContext(RouteContext routeContext) {
        routeContextStack.add(routeContext);
    }

    public RouteContext popRouteContext() {
        if (routeContextStack.isEmpty()) {
            return null;
        }
        return routeContextStack.pop();
    }

    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        // no wrapping needed
        return callback;
    }

    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        // noop
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
