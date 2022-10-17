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
package org.apache.camel.main;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default {@link RoutesCollector}.
 */
public class DefaultRoutesCollector implements RoutesCollector {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Collection<RoutesBuilder> collectRoutesFromRegistry(
            CamelContext camelContext,
            String excludePattern,
            String includePattern) {

        final List<RoutesBuilder> routes = new ArrayList<>();
        final AntPathMatcher matcher = new AntPathMatcher();

        Collection<RoutesBuilder> additional
                = collectAdditionalRoutesFromRegistry(camelContext, excludePattern, includePattern);
        if (additional != null) {
            routes.addAll(additional);
        }

        Collection<LambdaRouteBuilder> lrbs = findByType(camelContext, LambdaRouteBuilder.class);
        for (LambdaRouteBuilder lrb : lrbs) {
            RouteBuilder rb = new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    lrb.accept(this);
                }
            };
            routes.add(rb);
        }

        Collection<RoutesBuilder> builders = findByType(camelContext, RoutesBuilder.class);
        for (RoutesBuilder routesBuilder : builders) {
            // filter out abstract classes
            boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
            if (!abs) {
                String name = routesBuilder.getClass().getName();
                // make name as path so we can use ant path matcher
                name = name.replace('.', '/');

                boolean match = !"false".equals(includePattern);

                // special support for testing with @ExcludeRoutes annotation with camel-test modules
                String exclude = camelContext.adapt(ExtendedCamelContext.class).getTestExcludeRoutes();
                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(exclude)) {
                    // this property is a comma separated list of FQN class names, so we need to make
                    // name as path so we can use ant patch matcher
                    exclude = exclude.replace('.', '/');
                    // there may be multiple separated by comma
                    String[] parts = exclude.split(",");
                    for (String part : parts) {
                        // must negate when excluding, and hence !
                        match = !matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }

                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(excludePattern)) {
                    // there may be multiple separated by comma
                    String[] parts = excludePattern.split(",");
                    for (String part : parts) {
                        // must negate when excluding, and hence !
                        match = !matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }
                if (match && ObjectHelper.isNotEmpty(includePattern)) {
                    // there may be multiple separated by comma
                    String[] parts = includePattern.split(",");
                    for (String part : parts) {
                        match = matcher.match(part, name);
                        log.trace("Java RoutesBuilder: {} include filter: {} -> {}", name, part, match);
                        if (match) {
                            break;
                        }
                    }
                }
                log.debug("Java RoutesBuilder: {} accepted by include/exclude filter: {}", name, match);
                if (match) {
                    routes.add(routesBuilder);
                }
            }
        }

        return routes;
    }

    @Override
    public Collection<RoutesBuilder> collectRoutesFromDirectory(
            CamelContext camelContext,
            String excludePattern,
            String includePattern) {

        final ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
        final List<RoutesBuilder> answer = new ArrayList<>();
        final String[] includes = includePattern != null ? includePattern.split(",") : null;

        StopWatch watch = new StopWatch();
        Collection<Resource> accepted = findRouteResourcesFromDirectory(camelContext, excludePattern, includePattern);
        try {
            Collection<RoutesBuilder> builders = ecc.getRoutesLoader().findRoutesBuilders(accepted);
            if (!builders.isEmpty()) {
                log.debug("Found {} route builder from locations: {}", builders.size(), includes);
                answer.addAll(builders);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        if (!answer.isEmpty()) {
            log.debug("Loaded {} ({} millis) additional RoutesBuilder from: {}, pattern: {}", answer.size(), watch.taken(),
                    includes,
                    includePattern);
        } else {
            log.debug("No additional RoutesBuilder discovered from: {}", includePattern);
        }

        return answer;
    }

    @Override
    public Collection<Resource> findRouteResourcesFromDirectory(
            CamelContext camelContext,
            String excludePattern,
            String includePattern) {
        final ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
        final PackageScanResourceResolver resolver = ecc.getPackageScanResourceResolver();
        final String[] includes = includePattern != null ? includePattern.split(",") : null;
        final String[] excludes = excludePattern != null ? excludePattern.split(",") : null;

        if (includes == null || ObjectHelper.equal("false", includePattern)) {
            log.debug("Include pattern is empty/false, no routes will be discovered from resources");
            return new ArrayList<>();
        }

        Collection<Resource> accepted = new ArrayList<>();
        for (String include : includes) {
            log.debug("Finding additional routes from: {}", include);
            try {
                for (Resource resource : resolver.findResources(include)) {
                    // filter unwanted resources
                    if (!"false".equals(excludePattern) && AntPathMatcher.INSTANCE.anyMatch(excludes, resource.getLocation())) {
                        continue;
                    }
                    accepted.add(resource);
                }
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        return accepted;
    }

    /**
     * Strategy to allow collecting additional routes from registry.
     * 
     * @param camelContext   the context
     * @param excludePattern the exclusion pattern
     * @param includePattern the inclusion pattern
     */
    @SuppressWarnings("unused")
    protected Collection<RoutesBuilder> collectAdditionalRoutesFromRegistry(
            CamelContext camelContext,
            String excludePattern,
            String includePattern) {
        return null;
    }

    /**
     * Strategy to discover a specific route builder type from the registry. This allows Spring Boot or other runtimes
     * to do custom lookup.
     */
    protected <T> Collection<T> findByType(CamelContext camelContext, Class<T> type) {
        return camelContext.getRegistry().findByType(type);
    }

}
