/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
 * Collects routes and rests from the various sources (like registry or opinionated
 * classpath locations) and injects these into the Camel context.
 */
public class MainRoutesCollector {

    // TODO: Add load routes from xml and rest
    // TODO: Base class and extended for spring-boot etc

    private static final Logger LOG = LoggerFactory.getLogger(MainRoutesCollector.class);

    public List<RoutesBuilder> collectRoutes(CamelContext camelContext, DefaultConfigurationProperties configurationProperties) {
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

                String exclude = configurationProperties.getJavaRoutesExcludePattern();
                String include = configurationProperties.getJavaRoutesIncludePattern();

                boolean match = !"false".equals(include);
                // exclude take precedence over include
                if (match && ObjectHelper.isNotEmpty(exclude)) {
                    // there may be multiple separated by comma
                    String[] parts = exclude.split(",");
                    for (String part : parts) {
                        // must negate when excluding, and hence !
                        match = !matcher.match(part, name);
                        LOG.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }
                // special support for testing with @ExcludeRoutes annotation with camel-test-spring
                exclude = System.getProperty("CamelTestSpringExcludeRoutes");
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
                        LOG.trace("Java RoutesBuilder: {} exclude filter: {} -> {}", name, part, match);
                        if (!match) {
                            break;
                        }
                    }
                }
                if (match && ObjectHelper.isNotEmpty(include)) {
                    // there may be multiple separated by comma
                    String[] parts = include.split(",");
                    for (String part : parts) {
                        match = matcher.match(part, name);
                        LOG.trace("Java RoutesBuilder: {} include filter: {} -> {}", name, part, match);
                        if (match) {
                            break;
                        }
                    }
                }
                LOG.debug("Java RoutesBuilder: {} accepted by include/exclude filter: {}", name, match);
                if (match) {
                    routes.add(routesBuilder);
                }
            }


        }

        return routes;
    }

}
