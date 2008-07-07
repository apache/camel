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
package org.apache.camel.builder;

import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.model.ExceptionType;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a proxy to an error handler builder which is resolved by named reference
 *
 * @version $Revision$
 */
public class ErrorHandlerBuilderRef extends ErrorHandlerBuilderSupport {
    private final String ref;
    private ErrorHandlerBuilder handler;

    public ErrorHandlerBuilderRef(String ref) {
        this.ref = ref;
    }

    public ErrorHandlerBuilder copy() {
        return new ErrorHandlerBuilderRef(ref);
    }

    @Override
    public void addErrorHandlers(ExceptionType exception) {
        if (handler != null) {
            handler.addErrorHandlers(exception);
        }
        super.addErrorHandlers(exception);
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) throws Exception {
        if (handler == null) {
            handler = routeContext.lookup(ref, ErrorHandlerBuilder.class);
            ObjectHelper.notNull(handler, "error handler '" + ref + "'");
            List<ExceptionType> list = getExceptions();
            for (ExceptionType exceptionType : list) {
                handler.addErrorHandlers(exceptionType);
            }
        }
        return handler.createErrorHandler(routeContext, processor);
    }
}
