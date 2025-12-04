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

import java.util.concurrent.ThreadFactory;

import org.apache.camel.Consumer;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.MDC;

/**
 * MDC {@link org.apache.camel.spi.ExecutorServiceManager.ThreadFactoryListener} which will include the MDC information
 * for route id which allows MDC logging to pin-point to the route that logs. This makes it possible to include this
 * information earlier such as from the internal work that a consumer performs before routing
 * {@link org.apache.camel.Exchange} where the {@link MDCUnitOfWork} would include this information.
 */
public class MDCThreadFactoryListener implements ExecutorServiceManager.ThreadFactoryListener {

    @Override
    public ThreadFactory onNewThreadFactory(Object source, ThreadFactory factory) {
        if (source instanceof Consumer c && c instanceof RouteIdAware ra) {
            String name = c.getEndpoint().getCamelContext().getName();
            String routeId = ra.getRouteId();
            if (routeId != null) {
                return newThreadFactory(name, routeId, factory);
            }
        }
        return factory;
    }

    private ThreadFactory newThreadFactory(String contextName, String routeId, ThreadFactory tf) {
        return task -> {
            Runnable wrapped = () -> {
                MDC.put(UnitOfWork.MDC_CAMEL_CONTEXT_ID, contextName);
                MDC.put(UnitOfWork.MDC_ROUTE_ID, routeId);
                try {
                    task.run();
                } finally {
                    MDC.remove(UnitOfWork.MDC_CAMEL_CONTEXT_ID);
                    MDC.remove(UnitOfWork.MDC_ROUTE_ID);
                }
            };
            return tf.newThread(wrapped);
        };
    }
}
