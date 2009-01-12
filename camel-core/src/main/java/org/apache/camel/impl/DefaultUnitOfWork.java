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
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.TraceableUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.UuidGenerator;

/**
 * The default implementation of {@link UnitOfWork}
 *
 * @version $Revision$
 */
public class DefaultUnitOfWork implements TraceableUnitOfWork, Service {
    private static final UuidGenerator DEFAULT_ID_GENERATOR = new UuidGenerator();

    private String id;
    private List<Synchronization> synchronizations;
    private List<AsyncCallback> asyncCallbacks;
    private List<ProcessorType> routeList;

    public DefaultUnitOfWork() {
    }

    public void start() throws Exception {
        id = null;
    }

    public void stop() throws Exception {
        // need to clean up when we are stopping to not leak memory
        if (synchronizations != null) {
            synchronizations.clear();
        }
        if (asyncCallbacks != null) {
            asyncCallbacks.clear();
        }
        if (routeList != null) {
            routeList.clear();
        }
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

    public void done(Exchange exchange) {
        if (synchronizations != null) {
            boolean failed = exchange.isFailed();
            for (Synchronization synchronization : synchronizations) {
                if (failed) {
                    synchronization.onFailure(exchange);
                } else {
                    synchronization.onComplete(exchange);
                }
            }
        }
    }

    public boolean isSynchronous() {
        return asyncCallbacks == null || asyncCallbacks.isEmpty();
    }

    public String getId() {
        if (id == null) {
            id = DEFAULT_ID_GENERATOR.generateId();
        }
        return id;
    }

    public synchronized void addInterceptedNode(ProcessorType node) {
        if (routeList == null) {
            routeList = new ArrayList<ProcessorType>();
        }
        routeList.add(node);
    }

    public synchronized ProcessorType getLastInterceptedNode() {
        if (routeList == null || routeList.isEmpty()) {
            return null;
        }
        return routeList.get(routeList.size() - 1);
    }

    public List<ProcessorType> getInterceptedNodes() {
        return Collections.unmodifiableList(routeList);
    }

    /**
     * Register some asynchronous processing step
     */
    /*
    public synchronized AsyncCallback addAsyncStep() {
        AsyncCallback answer = new AsyncCallback() {
            public void done(boolean doneSynchronously) {
                latch.countDown();
            }
        };
        if (latch == null) {
            latch = new CountDownLatch(1);
        }
        else {
            // TODO increment latch!
        }
        if (asyncCallbacks == null) {
            asyncCallbacks = new ArrayList<AsyncCallback>();
        }
        asyncCallbacks.add(answer);
        return answer;
    }
    */
}
