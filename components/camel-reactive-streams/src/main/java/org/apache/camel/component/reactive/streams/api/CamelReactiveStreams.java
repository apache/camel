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
package org.apache.camel.component.reactive.streams.api;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main entry-point for getting Camel streams associate to reactive-streams endpoints.
 *
 * It allows to retrieve the {@link CamelReactiveStreamsService} to access Camel streams.
 * This class returns the default implementation of the service unless the client requests a named service,
 */
public final class CamelReactiveStreams {

    private static final Logger LOG = LoggerFactory.getLogger(CamelReactiveStreams.class);

    private static Map<CamelContext, String> serviceNames = new ConcurrentHashMap<>();

    private CamelReactiveStreams() {
    }

    public static CamelReactiveStreamsService get(CamelContext context) {
        return get(context, null);
    }

    public static CamelReactiveStreamsService get(CamelContext context, String serviceName) {
        if (serviceName != null && serviceName.trim().length() == 0) {
            throw new IllegalArgumentException("the service name cannot be an empty String");
        }

        String lookupName = serviceName != null ? serviceName : "";
        serviceNames.computeIfAbsent(context, ctx -> {
            CamelReactiveStreamsService service = context.hasService(CamelReactiveStreamsService.class);
            if (service == null) {
                service = resolveReactiveStreamsService(context, serviceName);
                try {
                    context.addService(service, true, true);
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot add the CamelReactiveStreamsService to the Camel context", ex);
                }
            }

            return lookupName;
        });

        if (!ObjectHelper.equal(serviceNames.get(context), lookupName)) {
            // only a single implementation of the CamelReactiveStreamService can be present per Camel context
            throw new IllegalArgumentException("Cannot use two different implementations of CamelReactiveStreamsService in the same CamelContext: "
                    + "existing service name [" + serviceNames.get(context) + "] - requested [" + lookupName + "]");
        }

        return context.hasService(CamelReactiveStreamsService.class);
    }

    private static CamelReactiveStreamsService resolveReactiveStreamsService(CamelContext context, String serviceName) {
        CamelReactiveStreamsService service = null;
        if (serviceName != null) {
            // lookup in the registry
            service = context.getRegistry().lookupByNameAndType(serviceName, CamelReactiveStreamsService.class);

            if (service == null) {
                service = resolveServiceUsingFactory(context, serviceName);
            }
        } else {
            Set<CamelReactiveStreamsService> set = context.getRegistry().findByType(CamelReactiveStreamsService.class);
            if (set.size() == 1) {
                service = set.iterator().next();
            }

            if (service == null) {
                LOG.info("Using default reactive stream service");
                service = resolveServiceUsingFactory(context, null);
            }
        }

        return service;
    }

    @SuppressWarnings("unchecked")
    private static CamelReactiveStreamsService resolveServiceUsingFactory(CamelContext context, String name) {
        if (name == null) {
            name = "default-service";
        }

        String path = "META-INF/services/org/apache/camel/reactive-streams/";
        Class<? extends CamelReactiveStreamsService> serviceClass = null;
        try {
            FactoryFinder finder = context.getFactoryFinder(path);
            LOG.trace("Using FactoryFinder: {}", finder);
            serviceClass = (Class<? extends CamelReactiveStreamsService>) finder.findClass(name);
            return serviceClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class referenced in '" + path + name + "' not found", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create the reactive stream service defined in '" + path + name + "'", e);
        }

    }

}
