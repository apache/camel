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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for developing custom {@link RoutePolicy} implementations.
 *
 * @version 
 */
public abstract class RoutePolicySupport extends ServiceSupport implements RoutePolicy {

    // TODO: Move to support package

    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    private ExceptionHandler exceptionHandler;

    public void onInit(Route route) {
        // noop
    }

    public void onRemove(Route route) {
        // noop
    }

    @Override
    public void onStart(Route route) {
        // noop
    }

    @Override
    public void onStop(Route route) {
        // noop
    }

    @Override
    public void onSuspend(Route route) {
        // noop
    }

    @Override
    public void onResume(Route route) {
        // noop
    }

    public void onExchangeBegin(Route route, Exchange exchange) {
        // noop
    }

    public void onExchangeDone(Route route, Exchange exchange) {
        // noop
    }

    protected boolean startConsumer(Consumer consumer) throws Exception {
        boolean resumed = ServiceHelper.resumeService(consumer);
        if (resumed) {
            log.debug("Resuming consumer {}", consumer);
        }
        return resumed;
    }

    protected boolean stopConsumer(Consumer consumer) throws Exception {
        boolean suspended = ServiceHelper.suspendService(consumer);
        if (suspended) {
            log.debug("Suspended consumer {}", consumer);
        }
        return suspended;
    }

    protected void startRoute(Route route) throws Exception {
        route.getRouteContext().getCamelContext().startRoute(route.getId());
    }

    protected void resumeRoute(Route route) throws Exception {
        route.getRouteContext().getCamelContext().resumeRoute(route.getId());
    }

    protected void suspendRoute(Route route) throws Exception {
        route.getRouteContext().getCamelContext().suspendRoute(route.getId());
    }

    protected void suspendRoute(Route route, long timeout, TimeUnit timeUnit) throws Exception {
        route.getRouteContext().getCamelContext().suspendRoute(route.getId(), timeout, timeUnit);
    }

    protected void stopRoute(Route route) throws Exception {
        route.getRouteContext().getCamelContext().stopRoute(route.getId());
    }

    protected void stopRoute(Route route, long timeout, TimeUnit timeUnit) throws Exception {
        route.getRouteContext().getCamelContext().stopRoute(route.getId(), timeout, timeUnit);
    }
    
    /**
     * Handles the given exception using the {@link #getExceptionHandler()}
     *
     * @param t the exception to handle
     */
    protected void handleException(Throwable t) {
        getExceptionHandler().handleException(t);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

}
