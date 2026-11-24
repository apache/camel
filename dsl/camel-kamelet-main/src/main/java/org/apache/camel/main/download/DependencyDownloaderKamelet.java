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
package org.apache.camel.main.download;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.Kamelet;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoaderSupport;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.main.util.VersionHelper;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ModelLifecycleStrategy;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

/**
 * To automatic downloaded dependencies that Kamelets requires.
 *
 * The dependencies of a Kamelet are downloaded when a route that uses the Kamelet is added, before the route is
 * created: the route can then use the classes of these dependencies, such as an exception class in a doCatch. The route
 * of the Kamelet itself is created later, when the CamelContext is initialized.
 */
public final class DependencyDownloaderKamelet extends ServiceSupport
        implements CamelContextAware, RouteTemplateLoaderListener, ModelLifecycleStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyDownloaderKamelet.class);

    private String kameletsVersion;
    private KameletDependencyDownloader downloader;
    private CamelContext camelContext;
    // the Kamelets whose dependencies are downloaded already, when a route that uses them is added
    private final Set<String> kamelets = ConcurrentHashMap.newKeySet();

    public DependencyDownloaderKamelet(CamelContext camelContext, String kameletsVersion) {
        this.camelContext = camelContext;
        this.kameletsVersion = kameletsVersion;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doBuild() throws Exception {
        var comp = camelContext.getComponent("kamelet");
        if (comp instanceof KameletComponent kc) {
            kc.setRouteTemplateLoaderListener(this);
        }
        if (camelContext instanceof ModelCamelContext mcc) {
            mcc.addModelLifecycleStrategy(this);
        }
    }

    @Override
    protected void doInit() throws Exception {
        if (kameletsVersion == null) {
            kameletsVersion = VersionHelper.extractKameletsVersion();
        }

        this.downloader = new KameletDependencyDownloader(camelContext, "yaml", kameletsVersion);
        this.downloader.setCamelContext(camelContext);
        ServiceHelper.initService(downloader);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(downloader);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(downloader);
    }

    @Override
    public void loadRouteTemplate(Resource resource) {
        if (resource.getLocation().endsWith(".yaml")) {
            try {
                downloader.doLoadRouteBuilder(resource);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }

    @Override
    public void onAddRouteDefinition(RouteDefinition routeDefinition) {
        // the Kamelets of the route are created after all the routes, when the CamelContext is initialized: their
        // dependencies are downloaded now, so the route can use their classes (CAMEL-27364)
        if (downloader == null) {
            return;
        }
        List<String> uris = new ArrayList<>();
        if (routeDefinition.getInput() != null) {
            uris.add(routeDefinition.getInput().getEndpointUri());
        }
        for (EndpointRequiredDefinition erd : ProcessorDefinitionHelper.filterTypeInOutputs(routeDefinition.getOutputs(),
                EndpointRequiredDefinition.class)) {
            uris.add(erd.getEndpointUri());
        }
        for (String uri : uris) {
            String name = kameletName(uri);
            if (name != null && kamelets.add(name)) {
                try {
                    Resource resource = resolveKamelet(name, kameletLocation(uri));
                    if (resource != null) {
                        LOG.debug("Downloading the dependencies of Kamelet: {} used by route: {}", name,
                                routeDefinition.getRouteId());
                        loadRouteTemplate(resource);
                    }
                } catch (Exception e) {
                    // the dependencies are then downloaded when the Kamelet is loaded, which reports the error
                    LOG.debug("Cannot download the dependencies of Kamelet: {} due to: {}", name, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public void onRemoveRouteDefinition(RouteDefinition routeDefinition) {
        // noop
    }

    @Override
    public void onAddRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) {
        // noop
    }

    @Override
    public void onRemoveRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) {
        // noop
    }

    /**
     * The name of the Kamelet of a kamelet: endpoint uri, or null when it is not one, it is kamelet:source or
     * kamelet:sink (the template of a Kamelet), or the name is a placeholder or an expression.
     */
    static String kameletName(String uri) {
        if (uri == null || !uri.startsWith("kamelet:")) {
            return null;
        }
        String name = FileUtil.stripLeadingSeparator(uri.substring("kamelet:".length()));
        int q = name.indexOf('?');
        if (q >= 0) {
            name = name.substring(0, q);
        }
        int slash = name.indexOf('/');
        if (slash >= 0) {
            // kamelet:name/routeId
            name = name.substring(0, slash);
        }
        if (name.isEmpty() || Kamelet.SOURCE_ID.equals(name) || Kamelet.SINK_ID.equals(name)
                || name.contains("{{") || name.contains("${")) {
            return null;
        }
        return name;
    }

    /**
     * The location of the Kamelets: the location option of the endpoint, otherwise the one of the kamelet component.
     */
    private String kameletLocation(String uri) {
        int q = uri.indexOf('?');
        if (q >= 0) {
            try {
                Map<String, Object> parameters = URISupport.parseQuery(uri.substring(q + 1));
                Object loc = parameters.get(Kamelet.PARAM_LOCATION);
                if (loc != null) {
                    return loc.toString();
                }
            } catch (Exception e) {
                // use the location of the component
                LOG.debug("Cannot parse the query of the kamelet endpoint: {} due to: {}. Using the location of the"
                          + " kamelet component.",
                        URISupport.sanitizeUri(uri), e.getMessage(), e);
            }
        }
        if (camelContext.getComponent("kamelet") instanceof KameletComponent kc) {
            return kc.getLocation();
        }
        return Kamelet.DEFAULT_LOCATION;
    }

    /**
     * The Kamelet file in the location, as the kamelet component finds it (RouteTemplateHelper), or null when it is not
     * there.
     */
    private Resource resolveKamelet(String name, String location) {
        if (location == null) {
            return null;
        }
        ResourceLoader resourceLoader = PluginHelper.getResourceLoader(camelContext);
        for (String path : location.split(",")) {
            // using dot as current dir must be expanded into absolute path
            if (".".equals(path) || "file:.".equals(path)) {
                path = "file:" + FileUtil.onlyPath(new File(".").getAbsolutePath());
            }
            Resource res = null;
            // a path with an extension is the Kamelet file itself
            String ext = FileUtil.onlyExt(path);
            if (ext != null && !ext.isEmpty()) {
                res = resourceLoader.resolveResource(path);
            }
            if (res == null || !res.exists()) {
                res = resourceLoader.resolveResource((path.endsWith("/") ? path : path + "/") + name + ".kamelet.yaml");
            }
            if (res != null && res.exists()) {
                return res;
            }
        }
        return null;
    }

    /**
     * To automatic downloaded dependencies that Kamelets requires.
     */
    private static class KameletDependencyDownloader extends YamlRoutesBuilderLoaderSupport {

        private static final Logger LOG = LoggerFactory.getLogger(KameletDependencyDownloader.class);
        private final CamelContext camelContext;
        private final DependencyDownloader downloader;
        private final Set<String> downloaded = new HashSet<>();
        private final String kameletsVersion;

        public KameletDependencyDownloader(CamelContext camelContext, String extension, String kameletsVersion) {
            super(extension);
            this.camelContext = camelContext;
            this.downloader = camelContext.hasService(DependencyDownloader.class);
            this.kameletsVersion = kameletsVersion;
        }

        @Override
        protected void doInit() throws Exception {
            super.doInit();

            MavenDependencyDownloader downloader = getCamelContext().hasService(MavenDependencyDownloader.class);
            if (!downloader.alreadyOnClasspath("org.apache.camel.kamelets", "camel-kamelets", kameletsVersion)) {
                downloader.downloadDependency("org.apache.camel.kamelets", "camel-kamelets", kameletsVersion, false);
            }
        }

        @Override
        protected RouteBuilder builder(YamlDeserializationContext ctx, Node node) {
            Node name = nodeAt(node, "/metadata/name");
            String text = YamlDeserializerSupport.asText(name);
            if (downloader != null) {
                downloader.onLoadingKamelet(text);
            }

            final List<String> dependencies = new ArrayList<>();

            Node deps = nodeAt(node, "/spec/dependencies");
            if (deps != null && deps.getNodeType() == NodeType.SEQUENCE) {
                SequenceNode sn = (SequenceNode) deps;
                for (Node child : sn.getValue()) {
                    if (child.getNodeType() == NodeType.SCALAR) {
                        ScalarNode scn = (ScalarNode) child;
                        String dep = scn.getValue();
                        if (dep != null) {
                            LOG.trace("Kamelet dependency: {}", dep);
                            dependencies.add(dep);
                        }
                    }
                }
            }

            if (downloader != null) {
                downloadDependencies(dependencies);
            }

            // need to fool and return an empty route builder
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    // noop
                }
            };
        }

        private void downloadDependencies(List<String> dependencies) {
            final List<String> gavs = new ArrayList<>();
            for (String dep : dependencies) {
                if ("camel:core".equals(dep) || "camel-core".equals(dep)
                        || "camel:kamelet".equals(dep) || "camel-kamelet".equals(dep)) {
                    continue;
                }
                String gav = dep;
                if (CatalogDependencyResolver.isCamelShorthand(dep)) {
                    MavenGav resolved = CatalogDependencyResolver.resolve(dep, camelContext.getVersion());
                    gav = resolved.getGroupId() + ":" + resolved.getArtifactId() + ":" + resolved.getVersion();
                } else if (dep.startsWith("camel-kamelets:")) {
                    // it's a known camel kamelets dependency
                    gav = "org.apache.camel.kamelets:camel-kamelets-" + dep.substring("camel-kamelets:".length()) + ":"
                          + kameletsVersion;
                }
                if (isValidGav(gav)) {
                    gavs.add(gav);
                }
            }

            if (!gavs.isEmpty()) {
                for (String gav : gavs) {
                    MavenGav mg = CatalogDependencyResolver.resolve(gav, camelContext.getVersion());
                    downloader.downloadDependency(mg.getGroupId(), mg.getArtifactId(), mg.getVersion());
                    downloaded.add(gav);
                }
            }
        }

        private boolean isValidGav(String gav) {
            if (downloaded.contains(gav)) {
                // already downloaded
                return false;
            }

            // skip camel-core and camel-kamelet as they are already included
            if (gav.contains("org.apache.camel:camel-core") || gav.contains("org.apache.camel:camel-kamelet:")) {
                return false;
            }

            MavenGav mg = CatalogDependencyResolver.resolve(gav, camelContext.getVersion());
            boolean exists = downloader.alreadyOnClasspath(mg.getGroupId(), mg.getArtifactId(), mg.getVersion());
            // valid if not already on classpath
            return !exists;
        }
    }
}
