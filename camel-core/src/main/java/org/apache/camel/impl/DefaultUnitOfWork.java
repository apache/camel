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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RouteNode;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.TraceableUnitOfWork;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The default implementation of {@link org.apache.camel.spi.UnitOfWork}
 *
 * @version $Revision$
 */
public class DefaultUnitOfWork implements TraceableUnitOfWork, Service {
    private static final transient Log LOG = LogFactory.getLog(DefaultUnitOfWork.class);
    private static final UuidGenerator DEFAULT_ID_GENERATOR = new UuidGenerator();

    private String id;
    private List<Synchronization> synchronizations;
    private List<RouteNode> routeNodes;
    private Map<ProcessorDefinition, AtomicInteger> routeIndex = new HashMap<ProcessorDefinition, AtomicInteger>();
    private Message originalInMessage;

    public DefaultUnitOfWork(Exchange exchange) {
        // special for JmsMessage as it can cause it to loose headers later. Yeah JMS suchs
        if (exchange.getIn().getClass().getSimpleName().equals("JmsMessage")) {
            this.originalInMessage = new DefaultMessage();
            this.originalInMessage.setBody(exchange.getIn().getBody());
            // cannot copy headers with a JmsMessage as the underlying javax.jms.Message object goes nuts 
        } else {
            this.originalInMessage = exchange.getIn().copy();
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
        if (routeNodes != null) {
            routeNodes.clear();
        }
        routeIndex.clear();
        originalInMessage = null;
    }

    public synchronized void addSynchronization(Synchronization synchronization) {
        if (synchronizations == null) {
            synchronizations = new ArrayList<Synchronization>();
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

        for (Synchronization synchronization : synchronizations) {
            target.addOnCompletion(synchronization);
        }

        // clear this list as its handed over to the other exchange
        this.synchronizations.clear();
    }

    public void done(Exchange exchange) {
        if (synchronizations != null && !synchronizations.isEmpty()) {
            boolean failed = exchange.isFailed();
            for (Synchronization synchronization : synchronizations) {
                try {
                    if (failed) {
                        synchronization.onFailure(exchange);
                    } else {
                        synchronization.onComplete(exchange);
                    }
                } catch (Exception e) {
                    // must catch exceptions to ensure all synchronizations have a chance to run
                    LOG.error("Exception occured during onCompletion. This exception will be ignored: ", e);
                }
            }
        }
    }

    public String getId() {
        if (id == null) {
            id = DEFAULT_ID_GENERATOR.generateId();
        }
        return id;
    }

    public void addTraced(RouteNode entry) {
        if (routeNodes == null) {
            routeNodes = new ArrayList<RouteNode>();
        }
        routeNodes.add(entry);
    }

    public RouteNode getLastNode() {
        if (routeNodes == null || routeNodes.isEmpty()) {
            return null;
        }
        return routeNodes.get(routeNodes.size() - 1);
    }

    public RouteNode getSecondLastNode() {
        if (routeNodes == null || routeNodes.isEmpty() || routeNodes.size() == 1) {
            return null;
        }
        return routeNodes.get(routeNodes.size() - 2);
    }

    public List<RouteNode> getNodes() {
        return Collections.unmodifiableList(routeNodes);
    }

    public Message getOriginalInMessage() {
        return originalInMessage;
    }

    public int getAndIncrement(ProcessorDefinition node) {
        AtomicInteger count = routeIndex.get(node);
        if (count == null) {
            count = new AtomicInteger();
            routeIndex.put(node, count);
        }
        return count.getAndIncrement();
    }

}
