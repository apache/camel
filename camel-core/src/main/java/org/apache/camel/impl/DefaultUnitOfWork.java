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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
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
    private List<Processor> processorList;
    private Object originalInBody;

    public DefaultUnitOfWork(Exchange exchange) {
        this.originalInBody = exchange.getIn().getBody();
    }

    public void start() throws Exception {
        id = null;
    }

    public void stop() throws Exception {
        // need to clean up when we are stopping to not leak memory
        if (synchronizations != null) {
            synchronizations.clear();
            synchronizations = null;
        }
        if (processorList != null) {
            processorList.clear();
            processorList = null;
        }
        originalInBody = null;
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

    public void addInterceptedProcessor(Processor processor) {
        if (processorList == null) {
            processorList = new ArrayList<Processor>();
        }
        processorList.add(processor);
    }

    public Processor getLastInterceptedProcessor() {
        if (processorList == null || processorList.isEmpty()) {
            return null;
        }
        return processorList.get(processorList.size() - 1);
    }

    public Processor getSecondLastInterceptedProcessor() {
        if (processorList == null || processorList.isEmpty() || processorList.size() == 1) {
            return null;
        }
        return processorList.get(processorList.size() - 2);
    }

    public List<Processor> getInterceptedProcessors() {
        return Collections.unmodifiableList(processorList);
    }

    public Object getOriginalInBody() {
        return originalInBody;
    }
}
