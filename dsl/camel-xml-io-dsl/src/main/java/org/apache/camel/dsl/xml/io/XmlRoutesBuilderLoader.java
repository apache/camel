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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.w3c.dom.Document;

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
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.CachedResource;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.scan.PackageScanHelper;
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

    private final Map<String, Boolean> preparseDone = new ConcurrentHashMap<>();
    private final Map<String, Resource> resourceCache = new ConcurrentHashMap<>();
    private final Map<String, XmlStreamInfo> xmlInfoCache = new ConcurrentHashMap<>();
    private final Map<String, BeansDefinition> camelAppCache = new ConcurrentHashMap<>();
    private final List<RegistryBeanDefinition> delayedRegistrations = new ArrayList<>();

    private final AtomicInteger counter = new AtomicInteger(0);

    public XmlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    XmlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        // preparsing is done at early stage, so we have a chance to load additional beans and populate
        // Camel registry
        if (preparseDone.getOrDefault(resource.getLocation(), false)) {
            return;
        }
        XmlStreamInfo xmlInfo = xmlInfo(resource);
        if (xmlInfo.isValid()) {
            String root = xmlInfo.getRootElementName();
            if ("beans".equals(root) || "camel".equals(root)) {
                new ModelParser(resource, xmlInfo.getRootElementNamespace())
                        .parseBeansDefinition()
                        .ifPresent(bd -> {
                            registerBeans(resource, bd);
                            camelAppCache.put(resource.getLocation(), bd);
                        });
            }
        }
        preparseDone.put(resource.getLocation(), true);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource input) throws Exception {
        final Resource resource = resource(input);
        XmlStreamInfo xmlInfo = xmlInfo(input);
        if (!xmlInfo.isValid()) {
            // should be valid, because we checked it before
            LOG.warn("Invalid XML document: {}", xmlInfo.getProblem().getMessage());
            return null;
        }

        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                String resourceLocation = input.getLocation();
                switch (xmlInfo.getRootElementName()) {
                    case "beans", "camel" -> {
                        BeansDefinition def = camelAppCache.get(resourceLocation);
                        if (def != null) {
                            configureCamel(def);
                        } else {
                            new ModelParser(resource, xmlInfo.getRootElementNamespace())
                                    .parseBeansDefinition()
                                    .ifPresent(this::configureCamel);
                        }
                    }
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

                // knowing this is the last time an XML may have been parsed, we can clear the cache
                // (route may get reloaded later)
                resourceCache.remove(resourceLocation);
                xmlInfoCache.remove(resourceLocation);
                camelAppCache.remove(resourceLocation);
                preparseDone.remove(resourceLocation);
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

            private void configureCamel(BeansDefinition app) {
                if (!delayedRegistrations.isEmpty()) {
                    // some of the beans were not available yet, so we have to try register them now
                    for (RegistryBeanDefinition bean : delayedRegistrations) {
                        registerBeanDefinition(bean, false);
                    }
                    delayedRegistrations.clear();
                }

                // we have access to beans and spring beans, but these are already processed
                // in preParseRoute() and possibly registered in
                // org.apache.camel.main.BaseMainSupport.postProcessCamelRegistry() (if given Main implementation
                // decides to do so)

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

    private Resource resource(Resource resource) {
        return resourceCache.computeIfAbsent(resource.getLocation(), l -> new CachedResource(resource));
    }

    private XmlStreamInfo xmlInfo(Resource resource) {
        return xmlInfoCache.computeIfAbsent(resource.getLocation(), l -> {
            try {
                // instead of parsing the document NxM times (for each namespace x root element combination),
                // we preparse it using XmlStreamDetector and then parse it fully knowing what's inside.
                // we could even do better, by passing already preparsed information through config file, but
                // it's getting complicated when using multiple files.
                XmlStreamDetector detector = new XmlStreamDetector(resource.getInputStream());
                return detector.information();
            } catch (IOException e) {
                XmlStreamInfo invalid = new XmlStreamInfo();
                invalid.setProblem(e);
                return invalid;
            }
        });
    }

    private void registerBeans(Resource resource, BeansDefinition app) {
        // <component-scan> - discover and register beans directly with Camel injection
        Set<String> packagesToScan = new LinkedHashSet<>();
        app.getComponentScanning().forEach(cs -> {
            packagesToScan.add(cs.getBasePackage());
        });
        PackageScanHelper.registerBeans(getCamelContext(), packagesToScan);

        // <bean>s - register Camel beans directly with Camel injection
        for (RegistryBeanDefinition bean : app.getBeans()) {
            registerBeanDefinition(bean, true);
        }

        // <s:bean>, <s:beans> and <s:alias> elements - all the elements in single BeansDefinition have
        // one parent org.w3c.dom.Document - and this is what we collect from each resource
        if (!app.getSpringBeans().isEmpty()) {
            Document doc = app.getSpringBeans().get(0).getOwnerDocument();
            // bind as Document, to be picked up later - bean id allows nice sorting
            // (can also be single ID - documents will get collected in LinkedHashMap, so we'll be fine)
            String id = String.format("spring-document:%05d:%s", counter.incrementAndGet(), resource.getLocation());
            getCamelContext().getRegistry().bind(id, doc);
        }
    }

    /**
     * Try to instantiate bean from the definition. Depending on the stage ({@link #preParseRoute} or
     * {@link #doLoadRouteBuilder}), a failure may lead to delayed registration.
     *
     * @param def
     * @param delayIfFailed
     */
    private void registerBeanDefinition(RegistryBeanDefinition def, boolean delayIfFailed) {
        String type = def.getType();
        String name = def.getName();
        if (name == null || "".equals(name.trim())) {
            name = type;
        }
        if (type != null && !type.startsWith("#")) {
            type = "#class:" + type;
            try {
                final Object target = PropertyBindingSupport.resolveBean(getCamelContext(), type);

                if (def.getProperties() != null && !def.getProperties().isEmpty()) {
                    PropertyBindingSupport.setPropertiesOnTarget(getCamelContext(), target, def.getProperties());
                }
                getCamelContext().getRegistry().unbind(name);
                getCamelContext().getRegistry().bind(name, target);
            } catch (Exception e) {
                if (delayIfFailed) {
                    delayedRegistrations.add(def);
                } else {
                    LOG.warn("Problem creating bean {}", type, e);
                }
            }
        }
    }

}
