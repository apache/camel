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
package org.apache.camel.blueprint;

import java.util.List;
import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.blueprint.container.BlueprintContainer;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances in the
 *  {@link org.osgi.service.blueprint.container.BlueprintContainer}.
 *
 * @version $Revision$
 */
public class ContextScanRouteBuilderFinder {
    private static final transient Log LOG = LogFactory.getLog(ContextScanRouteBuilderFinder.class);
    private final BlueprintContainer blueprintContainer;
    private final PackageScanFilter filter;

    public ContextScanRouteBuilderFinder(BlueprintCamelContext camelContext, PackageScanFilter filter) {
        this.blueprintContainer = camelContext.getBlueprintContainer();
        this.filter = filter;
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} instances that can be found in the context
     */
    public void appendBuilders(List<RoutesBuilder> list) {
        Map<String, RoutesBuilder> beans = BlueprintContainerRegistry.lookupByType(blueprintContainer, RoutesBuilder.class);

        for (Object key : beans.keySet()) {
            Object bean = beans.get(key);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found RouteBuilder with id: " + key + " -> " + bean);
            }

            // certain beans should be ignored
            if (shouldIgnoreBean(bean)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring RouteBuilder id: " + key);
                }
                continue;
            }

            if (!isFilteredClass(bean)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring filtered RouteBuilder id: " + key + " as class: " + bean.getClass());
                }
                continue;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding instantiated RouteBuilder id: " + key + " as class: " + bean.getClass());
            }
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