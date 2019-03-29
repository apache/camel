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
package org.apache.camel.impl.cloud;

import java.util.Optional;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.util.ObjectHelper;

public final class ServiceRegistryHelper {
    private ServiceRegistryHelper() {
    }

    public static Optional<ServiceRegistry> lookupService(CamelContext context) {
        return lookupService(context, ServiceRegistrySelectors.DEFAULT_SELECTOR);
    }

    public static Optional<ServiceRegistry> lookupService(CamelContext context, ServiceRegistry.Selector selector) {
        ObjectHelper.notNull(context, "Camel Context");
        ObjectHelper.notNull(selector, "ServiceRegistry selector");

        Set<ServiceRegistry> services = context.hasServices(ServiceRegistry.class);

        if (ObjectHelper.isNotEmpty(services)) {
            return selector.select(services);
        }

        return Optional.empty();
    }

    public static ServiceRegistry mandatoryLookupService(CamelContext context) {
        return lookupService(context).orElseThrow(() -> new IllegalStateException("ServiceRegistry service not found"));
    }

    public static ServiceRegistry mandatoryLookupService(CamelContext context, ServiceRegistry.Selector selector) {
        return lookupService(context, selector).orElseThrow(() -> new IllegalStateException("ServiceRegistry service not found"));
    }
}
