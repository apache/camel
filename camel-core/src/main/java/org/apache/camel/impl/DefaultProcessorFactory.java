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

import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;

/**
 * Default {@link ProcessorFactory} that supports using 3rd party Camel components to implement the EIP {@link Processor}.
 * <p/>
 * The component should use the {@link FactoryFinder} SPI to specify a file with the name of the EIP model in the
 * directory of {@link #RESOURCE_PATH}. The file should contain a property with key <tt>class</tt> that refers
 * to the name of the {@link ProcessorFactory} the Camel component implement, which gets called for creating
 * the {@link Processor}s for the EIP.
 * <p/>
 * The Hystrix EIP is such an example where {@link org.apache.camel.model.HystrixDefinition} is implemented
 * in the <tt>camel-hystrix</tt> component.
 */
public class DefaultProcessorFactory implements ProcessorFactory {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/model/";

    @Override
    public Processor createChildProcessor(RouteContext routeContext, ProcessorDefinition<?> definition, boolean mandatory) throws Exception {
        String name = definition.getClass().getSimpleName();
        FactoryFinder finder = routeContext.getCamelContext().getFactoryFinder(RESOURCE_PATH);
        try {
            if (finder != null) {
                Object object = finder.newInstance(name);
                if (object instanceof ProcessorFactory) {
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
                if (object instanceof ProcessorFactory) {
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
