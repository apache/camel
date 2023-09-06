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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoaderSupport;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.main.util.VersionHelper;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

/**
 * To automatic downloaded dependencies that Kamelets requires.
 */
public final class DependencyDownloaderKamelet extends ServiceSupport
        implements CamelContextAware, RouteTemplateLoaderListener {

    private String kameletsVersion;
    private KameletDependencyDownloader downloader;
    private CamelContext camelContext;

    public DependencyDownloaderKamelet(CamelContext camelContext) {
        this(camelContext, null);
    }

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
        KameletComponent kc = camelContext.getComponent("kamelet", KameletComponent.class);
        kc.setRouteTemplateLoaderListener(this);
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
                String gav = dep;
                if (dep.startsWith("camel:")) {
                    // it's a known camel component
                    gav = "org.apache.camel:camel-" + dep.substring("camel:".length()) + ":" + camelContext.getVersion();
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
                    MavenGav mg = MavenGav.parseGav(gav, camelContext.getVersion());
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

            MavenGav mg = MavenGav.parseGav(gav, camelContext.getVersion());
            boolean exists = downloader.alreadyOnClasspath(mg.getGroupId(), mg.getArtifactId(), mg.getVersion());
            // valid if not already on classpath
            return !exists;
        }
    }
}
