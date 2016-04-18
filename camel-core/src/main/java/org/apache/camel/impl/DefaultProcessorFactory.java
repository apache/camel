/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.impl;

import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;

public class DefaultProcessorFactory implements ProcessorFactory {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/model/";

    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        String name = definition.getClass().getSimpleName();
        FactoryFinder finder = routeContext.getCamelContext().getFactoryFinder(RESOURCE_PATH);
        try {
            if (finder != null) {
                Object object = finder.newInstance(name);
                if (object != null && object instanceof ProcessorFactory) {
                    ProcessorFactory pc = (ProcessorFactory) object;
                    return pc.createChildProcessor(routeContext, definition, mandatory);
                }
            }
        } catch (NoFactoryAvailableException e) {
            // ignore there is no custom factory
        }

        return null;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> definition) throws Exception {
        String name = definition.getClass().getSimpleName();
        FactoryFinder finder = routeContext.getCamelContext().getFactoryFinder(RESOURCE_PATH);
        try {
            if (finder != null) {
                Object object = finder.newInstance(name);
                if (object != null && object instanceof ProcessorFactory) {
                    ProcessorFactory pc = (ProcessorFactory) object;
                    return pc.createProcessor(routeContext, definition);
                }
            }
        } catch (NoFactoryAvailableException e) {
            // ignore there is no custom factory
        }

        return null;
    }
}
