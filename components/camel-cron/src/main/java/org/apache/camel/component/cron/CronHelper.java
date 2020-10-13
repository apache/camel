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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.component.cron.api.CamelCronService;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CronHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CronHelper.class);

    private static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/cron/";
    private static final String FACTORY_KEY = "cron-service";

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

        // Fallback to factory finder
        FactoryFinder finder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        return finder.newInstance(FACTORY_KEY, CamelCronService.class).orElse(null);
    }

}
