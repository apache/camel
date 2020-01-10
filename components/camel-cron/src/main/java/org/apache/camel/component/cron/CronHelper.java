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

import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CronHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CronHelper.class);

    private CronHelper() {
    }

    /**
     * Helper to lookup/create an instance of {@link CamelCronService}
     */
    public static CamelCronService resolveCamelCronService(CamelContext context, String name) {
        // Lookup the registry first
        CamelCronService service = ObjectHelper.isEmpty(name)
                ? CamelContextHelper.findByType(context, CamelCronService.class)
                : CamelContextHelper.lookup(context, name, CamelCronService.class);

        if (service != null) {
            // If the service is bound to the registry we assume it is already
            // configured so let's return it as it is.
            return service;
        }

        // Fallback to service loader
        Map<String, CamelCronService> services = new TreeMap<>();
        ServiceLoader.load(CamelCronService.class).forEach(s -> services.put(s.getId(), s));
        if (name != null) {
            return services.get(name);
        }
        if (services.size() == 1) {
            return services.values().iterator().next();
        } else if (services.size() > 1) {
            LOGGER.warn("Multiple implementations found for CamelCronService: {}", services.keySet());
        }
        return null;
    }

}
