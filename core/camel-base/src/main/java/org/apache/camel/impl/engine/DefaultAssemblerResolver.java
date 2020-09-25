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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.AssemblerResolver;
import org.apache.camel.spi.EndpointUriAssembler;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default assembler resolver that looks for assembler factories in
 * <b>META-INF/services/org/apache/camel/assembler/</b>.
 */
public class DefaultAssemblerResolver extends ServiceSupport implements CamelContextAware, AssemblerResolver {
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/assembler/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAssemblerResolver.class);

    private final Map<String, EndpointUriAssembler> cache = LRUCacheFactory.newLRUSoftCache(1000);
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
    public EndpointUriAssembler resolveAssembler(final String name, CamelContext context) {
        if (ObjectHelper.isEmpty(name)) {
            return null;
        }

        EndpointUriAssembler answer = cache.get(name);
        if (answer != null) {
            return answer;
        }

        // lookup in registry first
        Set<EndpointUriAssembler> assemblers = context.getRegistry().findByType(EndpointUriAssembler.class);
        answer = assemblers.stream().filter(a -> a.isEnabled(name)).findFirst().orElse(null);
        if (answer != null) {
            answer.setCamelContext(context);
            cache.put(name, answer);
            return answer;
        }

        // not in registry then use assembler factory for endpoints
        Class<?> type;
        try {
            type = findAssembler(name + "-endpoint", context);
        } catch (NoFactoryAvailableException e) {
            // its optional so its okay
            type = null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Assembler registered for scheme: " + name, e);
        }

        if (type != null) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Found assembler: {} via type: {} via: {}{}", name, type.getName(),
                        factoryFinder.getResourcePath(),
                        name);
            }

            // create the assembler
            if (EndpointUriAssembler.class.isAssignableFrom(type)) {
                answer = (EndpointUriAssembler) context.getInjector().newInstance(type, false);
                answer.setCamelContext(context);
                cache.put(name, answer);
                return answer;
            } else {
                throw new IllegalArgumentException(
                        "Type is not a EndpointUriAssembler implementation. Found: " + type.getName());
            }
        }

        return answer;
    }

    private Class<?> findAssembler(String name, CamelContext context) throws IOException {
        if (factoryFinder == null) {
            factoryFinder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

    protected Logger getLog() {
        return LOG;
    }

    @Override
    protected void doStop() throws Exception {
        cache.clear();
    }

}
