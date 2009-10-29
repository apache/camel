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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A base class for developing custom {@link RoutePolicy} implementations.
 *
 * @version $Revision$
 */
public abstract class RoutePolicySupport extends ServiceSupport implements RoutePolicy {

    protected final transient Log log = LogFactory.getLog(getClass());
    private ExceptionHandler exceptionHandler;

    public void onExchangeBegin(Route route, Exchange exchange) {
    }

    public void onExchangeDone(Route route, Exchange exchange) {
    }

    protected boolean startConsumer(Consumer consumer) throws Exception {
        if (consumer instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) consumer;
            if (ss.isSuspended()) {
                if (log.isDebugEnabled()) {
                    log.debug("Resuming consumer " + consumer);
                }
                ss.resume();
                return true;
            } else {
                return false;
            }
        } else if (consumer instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) consumer;
            if (ss.getStatus().isStartable()) {
                if (log.isDebugEnabled()) {
                    log.debug("Stopping consumer " + consumer);
                }
                consumer.start();
                return true;
            } else {
                return false;
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Stopping consumer " + consumer);
            }
            ServiceHelper.startService(consumer);
            return true;
        }
    }

    protected boolean stopConsumer(Consumer consumer) throws Exception {
        if (consumer instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) consumer;
            if (!ss.isSuspended()) {
                ss.suspend();
                if (log.isDebugEnabled()) {
                    log.debug("Suspending consumer " + consumer);
                }
                return true;
            } else {
                return false;
            }
        } else if (consumer instanceof ServiceSupport) {
            ServiceSupport ss = (ServiceSupport) consumer;
            if (ss.getStatus().isStoppable()) {
                if (log.isDebugEnabled()) {
                    log.debug("Stopping consumer " + consumer);
                }
                consumer.stop();
                return true;
            } else {
                return false;
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Stopping consumer " + consumer);
            }
            ServiceHelper.stopService(consumer);
            return true;
        }
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
    }

    @Override
    protected void doStop() throws Exception {
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
