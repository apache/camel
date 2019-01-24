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
package org.apache.camel.reifier;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.apache.camel.spi.RouteContext;

class ThrowExceptionReifier extends ProcessorReifier<ThrowExceptionDefinition> {

    ThrowExceptionReifier(ProcessorDefinition<?> definition) {
        super((ThrowExceptionDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        if (definition.getRef() != null && definition.getException() == null) {
            definition.setException(routeContext.getCamelContext().getRegistry().lookupByNameAndType(definition.getRef(), Exception.class));
        }

        if (definition.getExceptionType() != null && definition.getExceptionClass() == null) {
            try {
                definition.setExceptionClass(routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(definition.getExceptionType(), Exception.class));
            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        if (definition.getException() == null && definition.getExceptionClass() == null) {
            throw new IllegalArgumentException("exception or exceptionClass/exceptionType must be configured on: " + this);
        }
        return new ThrowExceptionProcessor(definition.getException(), definition.getExceptionClass(), definition.getMessage());
    }

}
