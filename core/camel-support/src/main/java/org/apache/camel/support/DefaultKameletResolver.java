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

package org.apache.camel.support;

import java.util.Collections;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.KameletResolver;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link org.apache.camel.spi.KameletResolver} which tries to find Kamelet definitions by
 * using the URI scheme prefix and searching for a file of the URI scheme name in the
 * <b>META-INF/services/org/apache/camel/kamelet/</b> directory on the classpath.
 */
public class DefaultKameletResolver implements KameletResolver {

    public static final String KAMELET_TRANSFORMER_RESOURCE_PATH = "META-INF/services/org/apache/camel/kamelet/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultKameletResolver.class);

    @Override
    public void resolve(String name, CamelContext context) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolving Kamelet definition for name {} via: {}{}", name, KAMELET_TRANSFORMER_RESOURCE_PATH, name);
        }

        Optional<RoutesBuilder> routeTemplate = findKameletRouteTemplate(name, context);
        if (LOG.isDebugEnabled() && routeTemplate.isPresent()) {
            LOG.debug("Found Kamelet definition for name {} via type: {} via: {}{}", name,
                    ObjectHelper.name(routeTemplate.getClass()), KAMELET_TRANSFORMER_RESOURCE_PATH, name);
        }

        if (routeTemplate.isPresent()) {
            CamelContextAware.trySetCamelContext(routeTemplate.get(), context);
            PluginHelper.getRoutesLoader(context).loadRoutesWithRoutesBuilders(Collections.singletonList(routeTemplate.get()));
        }
    }

    private Optional<RoutesBuilder> findKameletRouteTemplate(String name, CamelContext context) {
        return context.getCamelContextExtension()
                .getBootstrapFactoryFinder(KAMELET_TRANSFORMER_RESOURCE_PATH)
                .newInstance(name, RoutesBuilder.class);
    }
}
