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
package org.apache.camel.processor;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.annotations.JdkService;

/**
 * Default {@link ProcessorFactory} that supports using 3rd party Camel components to implement the EIP
 * {@link Processor}.
 * <p/>
 * The component should use the {@link FactoryFinder} SPI to specify a file with the name of the EIP model in the
 * directory of {@link #RESOURCE_PATH}. The file should contain a property with key <tt>class</tt> that refers to the
 * name of the {@link ProcessorFactory} the Camel component implement, which gets called for creating the
 * {@link Processor}s for the EIP.
 * <p/>
 * The Hystrix EIP is such an example where the circuit breaker EIP (CircuitBreakerDefinition) is implemented in the
 * <tt>camel-hystrix</tt> component.
 */
@JdkService(ProcessorFactory.FACTORY)
public class DefaultProcessorFactory implements ProcessorFactory {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/model/";

    @Override
    public Processor createChildProcessor(Route route, NamedNode definition, boolean mandatory) throws Exception {
        String name = definition.getClass().getSimpleName();
        FactoryFinder finder = route.getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        try {
            if (finder != null) {
                Object object = finder.newInstance(name);
                if (object instanceof ProcessorFactory) {
                    ProcessorFactory pc = (ProcessorFactory) object;
                    return pc.createChildProcessor(route, definition, mandatory);
                }
            }
        } catch (NoFactoryAvailableException e) {
            // ignore there is no custom factory
        }

        return null;
    }

    @Override
    public Processor createProcessor(Route route, NamedNode definition) throws Exception {
        String name = definition.getClass().getSimpleName();
        FactoryFinder finder = route.getCamelContext().adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        if (finder != null) {
            ProcessorFactory pc = finder.newInstance(name, ProcessorFactory.class).orElse(null);
            if (pc != null) {
                return pc.createProcessor(route, definition);
            }
        }

        return null;
    }

    // TODO: For map args then use Object[] as its faster

    @Override
    @SuppressWarnings("unchecked")
    public Processor createProcessor(CamelContext camelContext, String definitionName, Map<String, Object> args)
            throws Exception {
        if ("SendDynamicProcessor".equals(definitionName)) {
            String uri = (String) args.get("uri");
            Expression expression = (Expression) args.get("expression");
            ExchangePattern exchangePattern = (ExchangePattern) args.get("exchangePattern");
            SendDynamicProcessor processor = new SendDynamicProcessor(uri, expression);
            processor.setCamelContext(camelContext);
            if (exchangePattern != null) {
                processor.setPattern(exchangePattern);
            }
            return processor;
        } else if ("MulticastProcessor".equals(definitionName)) {
            Collection<Processor> processors = (Collection<Processor>) args.get("processors");
            ExecutorService executor = (ExecutorService) args.get("executor");
            boolean shutdownExecutorService = (boolean) args.get("shutdownExecutorService");
            return new MulticastProcessor(
                    camelContext, null, processors, null, true, executor, shutdownExecutorService, false, false, 0,
                    null, false, false);
        } else if ("ConvertBodyProcessor".equals(definitionName)) {
            Class<?> type = (Class<?>) args.get("type");
            return new ConvertBodyProcessor(type);
        }

        return null;
    }
}
