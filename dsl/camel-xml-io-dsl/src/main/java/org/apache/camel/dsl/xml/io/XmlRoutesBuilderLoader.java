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
package org.apache.camel.dsl.xml.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Named;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.CachedResource;
import org.apache.camel.support.DIRegistry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.xml.io.util.XmlStreamDetector;
import org.apache.camel.xml.io.util.XmlStreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed XML RoutesBuilderLoader")
@RoutesLoader(XmlRoutesBuilderLoader.EXTENSION)
public class XmlRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final Logger LOG = LoggerFactory.getLogger(XmlRoutesBuilderLoader.class);

    public static final String EXTENSION = "xml";
    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";
    private static final List<String> NAMESPACES = List.of("", NAMESPACE);

    public XmlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    XmlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource input) throws Exception {
        final Resource resource = new CachedResource(input);
        // instead of parsing the document NxM times (for each namespace x root element combination),
        // we preparse it using XmlStreamDetector and then parse it fully knowing what's inside.
        // we could even do better, by passing already preparsed information through config file, but
        // it's getting complicated when using multiple files.
        XmlStreamDetector detector = new XmlStreamDetector(resource.getInputStream());
        XmlStreamInfo xmlInfo = detector.information();
        if (!xmlInfo.isValid()) {
            // should be valid, because we checked it before
            LOG.warn("Invalid XML document: {}", xmlInfo.getProblem().getMessage());
            return null;
        }

        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                switch (xmlInfo.getRootElementName()) {
                    case "beans", "camel-app" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseBeansDefinition()
                                .ifPresent(this::allInOne);
                    case "routeTemplate", "routeTemplates" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseRouteTemplatesDefinition()
                                .ifPresent(this::setRouteTemplateCollection);
                    case "templatedRoutes", "templatedRoute" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseTemplatedRoutesDefinition()
                                .ifPresent(this::setTemplatedRouteCollection);
                    case "rests", "rest" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseRestsDefinition()
                                .ifPresent(this::setRestCollection);
                    case "routes", "route" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseRoutesDefinition()
                                .ifPresent(this::addRoutes);
                    default -> {
                    }
                }
            }

            @Override
            public void configuration() throws Exception {
                switch (xmlInfo.getRootElementName()) {
                    case "routeConfigurations", "routeConfiguration" ->
                        new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                .parseRouteConfigurationsDefinition()
                                .ifPresent(this::addConfigurations);
                    default -> {
                    }
                }
            }

            private void allInOne(BeansDefinition app) {
                // selected elements which can be found in camel-spring-xml's <camelContext>

                List<String> packagesToScan = new ArrayList<>();
                app.getComponentScanning().forEach(cs -> {
                    packagesToScan.add(cs.getBasePackage());
                });
                if (!packagesToScan.isEmpty()) {
                    DIRegistry registry = null;
                    DefaultRegistry camelRegistry = getCamelContext().getRegistry(DefaultRegistry.class);
                    if (camelRegistry != null) {
                        List<BeanRepository> repos = camelRegistry.getRepositories();
                        if (repos != null) {
                            Optional<BeanRepository> diRegistry
                                    = repos.stream().filter(r -> DIRegistry.class.isAssignableFrom(r.getClass())).findAny();
                            if (diRegistry.isPresent()) {
                                registry = (DIRegistry) diRegistry.get();
                            }
                        }
                    }
                    if (registry != null) {
                        PackageScanClassResolver scanner
                                = getCamelContext().getCamelContextExtension().getContextPlugin(PackageScanClassResolver.class);
                        if (scanner != null) {
                            for (String pkg : packagesToScan) {
                                Set<Class<?>> classes = scanner.findAnnotated(Named.class, pkg);
                                for (Class<?> c : classes) {
                                    registry.bind(c, c);
                                }
                            }
                        }
                    }
                }

                app.getRests().forEach(r -> {
                    List<RestDefinition> list = new ArrayList<>();
                    list.add(r);
                    RestsDefinition def = new RestsDefinition();
                    def.setRests(list);
                    setRestCollection(def);
                });

                app.getRouteConfigurations().forEach(rc -> {
                    List<RouteConfigurationDefinition> list = new ArrayList<>();
                    list.add(rc);
                    RouteConfigurationsDefinition def = new RouteConfigurationsDefinition();
                    def.setRouteConfigurations(list);
                    addConfigurations(def);
                });

                app.getRouteTemplates().forEach(rt -> {
                    List<RouteTemplateDefinition> list = new ArrayList<>();
                    list.add(rt);
                    RouteTemplatesDefinition def = new RouteTemplatesDefinition();
                    def.setRouteTemplates(list);
                    setRouteTemplateCollection(def);
                });

                app.getTemplatedRoutes().forEach(tr -> {
                    List<TemplatedRouteDefinition> list = new ArrayList<>();
                    list.add(tr);
                    TemplatedRoutesDefinition def = new TemplatedRoutesDefinition();
                    def.setTemplatedRoutes(list);
                    setTemplatedRouteCollection(def);
                });

                app.getRoutes().forEach(r -> {
                    List<RouteDefinition> list = new ArrayList<>();
                    list.add(r);
                    RoutesDefinition def = new RoutesDefinition();
                    def.setRoutes(list);
                    addRoutes(def);
                });
            }

            private void addRoutes(RoutesDefinition routes) {
                CamelContextAware.trySetCamelContext(routes, getCamelContext());

                // xml routes must be prepared in the same way java-dsl (via RoutesDefinition)
                // so create a copy and use the fluent builder to add the route
                for (RouteDefinition route : routes.getRoutes()) {
                    getRouteCollection().route(route);
                }
            }

            private void addConfigurations(RouteConfigurationsDefinition configurations) {
                CamelContextAware.trySetCamelContext(configurations, getCamelContext());

                // xml routes must be prepared in the same way java-dsl (via RouteConfigurationDefinition)
                // so create a copy and use the fluent builder to add the route
                for (RouteConfigurationDefinition config : configurations.getRouteConfigurations()) {
                    getRouteConfigurationCollection().routeConfiguration(config);
                }
            }
        };
    }
}
