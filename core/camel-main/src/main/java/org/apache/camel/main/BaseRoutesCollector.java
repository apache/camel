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
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base {@link RoutesCollector}.
 */
public abstract class BaseRoutesCollector implements RoutesCollector {

    // TODO: Add to camel main that it uses route collector

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public List<RoutesBuilder> collectRoutesFromRegistry(CamelContext camelContext,
                                                         String excludePattern, String includePattern) {
        final List<RoutesBuilder> routes = new ArrayList<>();

        final AntPathMatcher matcher = new AntPathMatcher();
        Set<RoutesBuilder> builders = camelContext.getRegistry().findByType(RoutesBuilder.class);
        for (RoutesBuilder routesBuilder : builders) {
            // filter out abstract classes
            boolean abs = Modifier.isAbstract(routesBuilder.getClass().getModifiers());
            if (!abs) {
                String name = routesBuilder.getClass().getName();
                // make name as path so we can use ant path matcher
                name = name.replace('.', '/');

                boolean match = !"false".equals(includePattern);
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
                // special support for testing with @ExcludeRoutes annotation with camel-test-spring
                excludePattern = System.getProperty("CamelTestSpringExcludeRoutes");
                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(excludePattern)) {
                    // this property is a comma separated list of FQN class names, so we need to make
                    // name as path so we can use ant patch matcher
                    excludePattern = excludePattern.replace('.', '/');
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

}
