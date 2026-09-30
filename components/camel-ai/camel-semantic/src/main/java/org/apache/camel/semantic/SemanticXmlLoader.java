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
package org.apache.camel.semantic;

import java.io.InputStream;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.CachedResource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.apache.camel.support.service.ServiceHelper;

/** Detects semantic declarations in ordinary XML route resources and delegates other XML to the configured loader. */
final class SemanticXmlLoader extends RoutesBuilderLoaderSupport {
    static final String REGISTRY_KEY = "semantic-xml-routes-loader";
    private RoutesBuilderLoader delegate;
    private final SemanticXmlRoutesBuilderLoader semantic = new SemanticXmlRoutesBuilderLoader();
    private Registry loaderRegistry;
    private List<RoutesBuilderLoader> otherLoaders = List.of();

    @Override
    public String getSupportedExtension() {
        return "xml";
    }

    @Override
    public boolean isSupportedExtension(String extension) {
        return ("xml".equals(extension) || extension.endsWith(".xml"))
                && otherLoaders().stream().noneMatch(loader -> loader.isSupportedExtension(extension));
    }

    private synchronized List<RoutesBuilderLoader> otherLoaders() {
        Registry registry = getCamelContext().getRegistry();
        if (loaderRegistry != registry) {
            // Discover lazily: the context may build before application loaders are registered.
            otherLoaders = registry.findByType(RoutesBuilderLoader.class).stream().filter(loader -> loader != this).toList();
            loaderRegistry = registry;
        }
        return otherLoaders;
    }

    synchronized void resetLoaderDiscovery() {
        loaderRegistry = null;
        otherLoaders = List.of();
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        Resource snapshot = snapshot(resource);
        if (hasDeclarations(snapshot)) {
            semantic.setCamelContext(getCamelContext());
            semantic.preParseRoute(snapshot);
        } else {
            delegate().preParseRoute(snapshot);
        }
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        Resource snapshot = snapshot(resource);
        if (hasDeclarations(snapshot)) {
            semantic.setCamelContext(getCamelContext());
            return semantic.loadRoutesBuilder(snapshot);
        }
        RoutesBuilder answer = delegate().loadRoutesBuilder(snapshot);
        if (answer instanceof RouteBuilder builder) {
            // Declarations removed from a resource are discarded only after its replacement configures successfully.
            builder.addLifecycleInterceptor(new RouteBuilderLifecycleStrategy() {
                @Override
                public void afterConfigure(RouteBuilder routeBuilder) {
                    SemanticQuestions questions
                            = getCamelContext().getCamelContextExtension().getContextPlugin(SemanticQuestions.class);
                    if (questions != null) {
                        questions.remove(resource.getLocation());
                    }
                }
            });
        }
        return answer;
    }

    private synchronized RoutesBuilderLoader delegate() throws Exception {
        if (delegate == null) {
            // Resolve the service directly: looking up "xml" through RoutesLoader would find this wrapper again.
            var finder
                    = getCamelContext().getCamelContextExtension().getBootstrapFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);
            RoutesBuilderLoader loader = ResolverHelper
                    .resolveService(getCamelContext(), finder, "xml", RoutesBuilderLoader.class)
                    .orElseThrow(() -> new IllegalStateException("An XML routes loader such as camel-xml-io-dsl is required"));
            loader.setCamelContext(getCamelContext());
            PluginHelper.getRoutesLoader(getCamelContext()).initRoutesBuilderLoader(loader);
            ServiceHelper.startService(loader);
            delegate = loader;
        }
        return delegate;
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegate, semantic);
        delegate = null;
        resetLoaderDiscovery();
        super.doStop();
    }

    private static Resource snapshot(Resource resource) {
        // Share bytes only within one call. Preserve the original scheme/existence for deletion tracking.
        return new CachedResource(resource) {
            @Override
            public String getScheme() {
                return resource.getScheme();
            }
        };
    }

    private static boolean hasDeclarations(Resource resource) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        try (InputStream stream = resource.getInputStream()) {
            XMLStreamReader reader = factory.createXMLStreamReader(stream);
            try {
                int depth = 0;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        depth++;
                        if (depth == 1) {
                            if ("semantic".equals(reader.getLocalName())
                                    || SemanticXmlRoutesBuilderLoader.NAMESPACE.equals(reader.getNamespaceURI())) {
                                return true;
                            }
                            if (!"routes".equals(reader.getLocalName())) {
                                return false;
                            }
                        } else if (depth == 2) {
                            if ("semantic".equals(reader.getLocalName())) {
                                return true;
                            }
                            // Only direct children can declare questions. Consume nested route content without
                            // inspecting names or namespaces, then continue looking for declarations after routes.
                            skipSubtree(reader);
                            depth--;
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (--depth == 0) {
                            return false;
                        }
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        }
    }

    private static void skipSubtree(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth != 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
