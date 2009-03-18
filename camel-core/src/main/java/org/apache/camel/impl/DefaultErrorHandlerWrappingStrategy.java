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

import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ErrorHandlerWrappingStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * The default error handler wrapping strategy used when JMX is disabled.
 *
 * @version $Revision$
 */
public class DefaultErrorHandlerWrappingStrategy implements ErrorHandlerWrappingStrategy {

    private final RouteContext routeContext;
    private final List<ProcessorDefinition> counterList;

    public DefaultErrorHandlerWrappingStrategy(RouteContext routeContext, List<ProcessorDefinition> counterList) {
        this.routeContext = routeContext;
        this.counterList = counterList;
    }

    public Processor wrapProcessorInErrorHandler(ProcessorDefinition processorDefinition, Processor target) throws Exception {

        // don't wrap our instrumentation interceptors
        if (counterList.contains(processorDefinition)) {
            return processorDefinition.getErrorHandlerBuilder().createErrorHandler(routeContext, target);
        }

        return target;
    }

}
