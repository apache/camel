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
package org.apache.camel.reifier.errorhandler;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.util.ObjectHelper;

public class ErrorHandlerRefReifier extends ErrorHandlerReifier<ErrorHandlerBuilderRef> {

    public ErrorHandlerRefReifier(Route route, ErrorHandlerFactory definition) {
        super(route, (ErrorHandlerBuilderRef)definition);
    }

    @Override
    public Processor createErrorHandler(Processor processor) throws Exception {
        ErrorHandlerFactory handler = lookupErrorHandler(route);
        return ErrorHandlerReifier.reifier(route, handler).createErrorHandler(processor);
    }

    private ErrorHandlerFactory lookupErrorHandler(Route route) {
        ErrorHandlerFactory handler =
                ErrorHandlerReifier.lookupErrorHandlerFactory(route, definition.getRef());
        ObjectHelper.notNull(handler, "error handler '" + definition.getRef() + "'");
        route.addErrorHandlerFactoryReference(definition, handler);
        return handler;
    }

}
