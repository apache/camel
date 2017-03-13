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

import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;

public class TypedProcessorFactory<T extends ProcessorDefinition<T>> implements ProcessorFactory {
    private final Class<T> type;

    protected TypedProcessorFactory(Class<T> type) {
        this.type = type;
    }

    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        if (definition != null && type.isInstance(definition)) {
            return doCreateChildProcessor(routeContext, type.cast(definition), mandatory);
        }

        return null;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception {
        if (definition != null && type.isInstance(definition)) {
            return doCreateProcessor(routeContext, type.cast(definition));
        }

        return null;
    }


    protected Processor doCreateChildProcessor(RouteContext routeContext, T definition, boolean mandatory) throws Exception {
        return null;
    }

    public Processor doCreateProcessor(RouteContext routeContext, T definition) throws Exception {
        return null;
    }
}
