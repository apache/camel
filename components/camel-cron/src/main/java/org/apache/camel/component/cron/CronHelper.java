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
package org.apache.camel.component.cron;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.util.ObjectHelper;

public final class CronHelper {

    private static final String FACTORY_KEY = "cron/cron-service";

    private CronHelper() {
    }

    /**
     * Helper to lookup/create an instance of {@link CamelCronService}.
     * <p>
     * Without a name, the only {@link CamelCronService} of the registry is used, or else the one that a module on the
     * classpath provides. With a name, the bean of the registry with this name is used, or else the cron service of a
     * module on the classpath whose id is the name, such as quartz or spring. A name that matches neither fails.
     */
    public static CamelCronService resolveCamelCronService(CamelContext context, String name) {
        // Lookup the registry first
        CamelCronService service = ObjectHelper.isEmpty(name)
                ? CamelContextHelper.findSingleByType(context, CamelCronService.class)
                : CamelContextHelper.lookup(context, name, CamelCronService.class);

        if (service != null) {
            // If the service is bound to the registry we assume it is already
            // configured so let's return it as it is.
            return service;
        }

        if (ObjectHelper.isEmpty(name)) {
            // Fallback to factory finder
            return ResolverHelper.resolveBootstrapService(context, FACTORY_KEY, CamelCronService.class).orElse(null);
        }

        // the cron service of a module on the classpath, by its id, such as cron/cron-service-quartz
        service = ResolverHelper.resolveBootstrapService(context, FACTORY_KEY + "-" + name, CamelCronService.class)
                .orElse(null);
        if (service != null) {
            return service;
        }
        // a module that has no cron service file of its id: its cron service when the id matches
        service = ResolverHelper.resolveBootstrapService(context, FACTORY_KEY, CamelCronService.class).orElse(null);
        if (service != null && name.equals(service.getId())) {
            return service;
        }
        throw new IllegalArgumentException(
                "Cannot find the CamelCronService " + name + " of the cronService option: no CamelCronService bean has"
                                           + " this name, and no module on the classpath provides a cron service with"
                                           + " this id (such as quartz for camel-quartz, or spring for camel-spring)"
                                           + (service != null
                                                   ? "; the cron service on the classpath has the id "
                                                     + service.getId()
                                                   : ""));
    }

}
