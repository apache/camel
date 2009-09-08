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
package org.apache.camel.management.mbean;

import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.spi.RouteContext;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed ErrorHandler")
public class ManagedErrorHandler {

    private RouteContext routeContext;
    private Processor errorHandler;
    private ErrorHandlerBuilder errorHandlerBuilder;

    public ManagedErrorHandler(RouteContext routeContext, Processor errorHandler, ErrorHandlerBuilder builder) {
        this.routeContext = routeContext;
        this.errorHandler = errorHandler;
        this.errorHandlerBuilder = builder;
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    public Processor getErrorHandler() {
        return errorHandler;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return routeContext.getCamelContext().getName();
    }

    @ManagedAttribute(description = "Does the error handler support redelivery")
    public boolean isSupportRedelivery() {
        return errorHandler instanceof RedeliveryErrorHandler;
    }

    @ManagedAttribute(description = "RedeliveryPolicy for maximum redeliveries")
    public Integer getMaximumRedeliveries() {
        if (errorHandler instanceof RedeliveryErrorHandler) {
            RedeliveryErrorHandler redelivery = (RedeliveryErrorHandler) errorHandler;
            return redelivery.getRedeliveryPolicy().getMaximumRedeliveries();
        }
        // not supported
        return null;
    }

    // TODO: work in progress

}
