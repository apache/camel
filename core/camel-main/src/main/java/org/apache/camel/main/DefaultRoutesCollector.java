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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.IOHelper;
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
    public List<RoutesDefinition> collectXmlRoutesFromDirectory(CamelContext camelContext, String directory) {
        List<RoutesDefinition> answer = new ArrayList<>();

        PackageScanResourceResolver resolver = camelContext.adapt(ExtendedCamelContext.class).getPackageScanResourceResolver();

        StopWatch watch = new StopWatch();
        int count = 0;
        String[] parts = directory.split(",");
        for (String part : parts) {
            log.debug("Loading additional Camel XML routes from: {}", part);
            try {
                Set<InputStream> set = resolver.findResources(part);
                for (InputStream is : set) {
                    log.debug("Found XML routes from location: {}", part);
                    ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                    RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
                    answer.add(routes);
                    IOHelper.close(is);
                    count += routes.getRoutes().size();
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML routes found in {}. Skipping XML routes detection.", part);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
            if (count > 0) {
                log.info("Loaded {} ({} millis) additional Camel XML routes from: {}", count, watch.taken(), directory);
            } else {
                log.info("No additional Camel XML routes discovered from: {}", directory);
            }
        }

        return answer;
    }

    @Override
    public List<RestsDefinition> collectXmlRestsFromDirectory(CamelContext camelContext, String directory) {
        List<RestsDefinition> answer = new ArrayList<>();

        PackageScanResourceResolver resolver = camelContext.adapt(ExtendedCamelContext.class).getPackageScanResourceResolver();

        StopWatch watch = new StopWatch();
        int count = 0;
        String[] parts = directory.split(",");
        for (String part : parts) {
            log.debug("Loading additional Camel XML rests from: {}", part);
            try {
                Set<InputStream> set = resolver.findResources(part);
                for (InputStream is : set) {
                    log.debug("Found XML rest from location: {}", part);
                    ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
                    RestsDefinition rests = (RestsDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
                    answer.add(rests);
                    IOHelper.close(is);
                    count += rests.getRests().size();
                }
            } catch (FileNotFoundException e) {
                log.debug("No XML rests found in {}. Skipping XML rests detection.", part);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
            if (count > 0) {
                log.info("Loaded {} ({} millis) additional Camel XML rests from: {}", count, watch.taken(), directory);
            } else {
                log.info("No additional Camel XML rests discovered from: {}", directory);
            }
        }

        return answer;
    }

}
