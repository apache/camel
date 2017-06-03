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
package org.apache.camel.component.routebox;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RouteboxServiceSupport extends ServiceSupport {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private ExceptionHandler exceptionHandler;
    private RouteboxEndpoint endpoint;
    private ExecutorService executor;
    private volatile boolean startedInnerContext;

    public RouteboxServiceSupport(RouteboxEndpoint endpoint) {
        this.endpoint = endpoint;
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
        }
    }
    
    protected void doStopInnerContext() throws Exception {
        CamelContext context = endpoint.getConfig().getInnerContext();
        context.stop();
        setStartedInnerContext(false);
    }

    protected void doStartInnerContext() throws Exception {
        // Add Route Builders and definitions to the inner camel context and start the context
        CamelContext context = endpoint.getConfig().getInnerContext();
        List<RouteBuilder> routeBuildersList = endpoint.getConfig().getRouteBuilders();
        if (!(routeBuildersList.isEmpty())) {
            for (RouteBuilder routeBuilder : routeBuildersList) {
                if (log.isDebugEnabled()) {
                    log.debug("Adding RouteBuilder {} to {}", routeBuilder, context.getName());
                }
                context.addRoutes(routeBuilder);
            }
        }       
        
        context.start();
        setStartedInnerContext(true);
    }

    public RouteboxEndpoint getRouteboxEndpoint() {
        return endpoint;
    }

    public void setRouteboxEndpoint(RouteboxEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setStartedInnerContext(boolean startedInnerContext) {
        this.startedInnerContext = startedInnerContext;
    }

    public boolean isStartedInnerContext() {
        return startedInnerContext;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
}
