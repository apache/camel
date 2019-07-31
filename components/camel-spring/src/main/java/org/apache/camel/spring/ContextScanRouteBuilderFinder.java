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
package org.apache.camel.spring;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances on the
 * Spring {@link org.springframework.context.ApplicationContext}.
 */
public class ContextScanRouteBuilderFinder {
    private static final Logger LOG = LoggerFactory.getLogger(ContextScanRouteBuilderFinder.class);
    private final ApplicationContext applicationContext;
    private final PackageScanFilter filter;
    private final boolean includeNonSingletons;

    public ContextScanRouteBuilderFinder(SpringCamelContext camelContext, PackageScanFilter filter, boolean includeNonSingletons) {
        this.applicationContext = camelContext.getApplicationContext();
        this.filter = filter;
        this.includeNonSingletons = includeNonSingletons;
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} instances that can be found in the context
     */
    public void appendBuilders(List<RoutesBuilder> list) {
        Map<String, RoutesBuilder> beans = applicationContext.getBeansOfType(RoutesBuilder.class, includeNonSingletons, true);

        for (Entry<String, RoutesBuilder> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Object key = entry.getKey();

            LOG.trace("Found RouteBuilder with id: {} -> {}", key, bean);

            // certain beans should be ignored
            if (shouldIgnoreBean(bean)) {
                LOG.debug("Ignoring RouteBuilder id: {}", key);
                continue;
            }

            if (!isFilteredClass(bean)) {
                LOG.debug("Ignoring filtered RouteBuilder id: {} as class: {}", key, bean.getClass());
                continue;
            }

            LOG.debug("Adding instantiated RouteBuilder id: {} as class: {}", key, bean.getClass());
            list.add((RoutesBuilder) bean);
        }
    }

    protected boolean shouldIgnoreBean(Object bean) {
        return false;
    }

    protected boolean isFilteredClass(Object bean) {
        if (filter != null) {
            return filter.matches(bean.getClass());
        } else {
            return false;
        }
    }

}
