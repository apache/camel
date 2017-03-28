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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Ordered;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.Service;
import org.apache.camel.StartupListener;
import org.apache.camel.util.ServiceHelper;

/**
 * A {@link org.apache.camel.StartupListener} that defers starting {@link Service}s, until as late as possible during
 * the startup process of {@link CamelContext}.
 */
public class DeferServiceStartupListener implements StartupListener, Ordered {

    private final Set<Service> services = new CopyOnWriteArraySet<Service>();

    public void addService(Service service) {
        services.add(service);
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        // new services may be added while starting a service
        // so use a while loop to get the newly added services as well
        while (!services.isEmpty()) {
            Service service = services.iterator().next();
            try {
                ServiceHelper.startService(service);
            } catch (Exception e) {
                if (service instanceof Endpoint) {
                    Endpoint endpoint = (Endpoint) service;
                    throw new ResolveEndpointFailedException(endpoint.getEndpointUri(), e);
                } else {
                    throw e;
                }
            } finally {
                services.remove(service);
            }
        }
    }

    public int getOrder() {
        // we want to be last, so the other startup listeners run first
        return Ordered.LOWEST;
    }
}
