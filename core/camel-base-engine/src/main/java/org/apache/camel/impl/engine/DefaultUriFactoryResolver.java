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
package org.apache.camel.impl.engine;

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default assembler resolver that looks for {@link UriFactoryResolver} factories in
 * <b>META-INF/services/org/apache/camel/urifactory/</b>.
 */
public class DefaultUriFactoryResolver implements CamelContextAware, UriFactoryResolver {
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/urifactory/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUriFactoryResolver.class);

    private CamelContext camelContext;
    private FactoryFinder factoryFinder;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public EndpointUriFactory resolveFactory(final String name, CamelContext context) {
        if (ObjectHelper.isEmpty(name)) {
            return null;
        }

        // lookup in registry first
        Set<EndpointUriFactory> assemblers = context.getRegistry().findByType(EndpointUriFactory.class);
        EndpointUriFactory answer = assemblers.stream().filter(a -> a.isEnabled(name)).findFirst().orElse(null);
        if (answer != null) {
            answer.setCamelContext(context);
            return answer;
        }

        // not in registry then use assembler factory for endpoints
        Class<?> type;
        try {
            type = findFactory(name + "-endpoint", context);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no EndpointUriFactory registered for scheme: " + name, e);
        }

        if (type != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found EndpointUriFactory: {} via type: {} via: {}{}", name, type.getName(),
                        factoryFinder.getResourcePath(),
                        name);
            }

            // create the assembler
            if (EndpointUriFactory.class.isAssignableFrom(type)) {
                answer = (EndpointUriFactory) context.getInjector().newInstance(type, false);
                answer.setCamelContext(context);
                return answer;
            } else {
                throw new IllegalArgumentException(
                        "Type is not a EndpointUriFactory implementation. Found: " + type.getName());
            }
        }

        return answer;
    }

    private Class<?> findFactory(String name, CamelContext context) {
        if (factoryFinder == null) {
            factoryFinder = context.getCamelContextExtension().getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

}
