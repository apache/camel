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
package org.apache.camel.cdi.xml;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} beans
 * in the {@link BeanManager}.
 */
final class ContextScanRouteBuilderFinder {

    private static final AnnotationLiteral<Any> ANY = new AnnotationLiteral<Any>() {
    };

    private final Logger logger = LoggerFactory.getLogger(ContextScanRouteBuilderFinder.class);

    private final BeanManager manager;

    private final PackageScanFilter filter;

    private final boolean includeNonSingletons;

    ContextScanRouteBuilderFinder(BeanManager manager, PackageScanFilter filter, boolean includeNonSingletons) {
        this.manager = manager;
        this.filter = filter;
        this.includeNonSingletons = includeNonSingletons;
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} bean instances that can be found in the manager.
     */
    void appendBuilders(List<RoutesBuilder> list) {
        for (Bean<?> bean : manager.getBeans(RoutesBuilder.class, ANY)) {
            logger.trace("Found RouteBuilder bean {}", bean);

            // certain beans should be ignored
            if (shouldIgnoreBean(bean)) {
                logger.debug("Ignoring RouteBuilder {}", bean);
                continue;
            }

            if (!isFilteredClass(bean)) {
                logger.debug("Ignoring filtered RouteBuilder {}", bean);
                continue;
            }

            logger.debug("Adding instantiated RouteBuilder {}", bean);
            Object instance = manager.getReference(bean, RoutesBuilder.class, manager.createCreationalContext(bean));
            list.add((RoutesBuilder) instance);
        }
    }

    private boolean shouldIgnoreBean(Bean<?> bean) {
        return !includeNonSingletons && !ApplicationScoped.class.equals(bean.getScope());
    }

    private boolean isFilteredClass(Bean<?> bean) {
        return filter != null && filter.matches(bean.getBeanClass());
    }
}
