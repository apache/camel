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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoaderSupport;
import org.apache.camel.spi.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

/**
 * Reuse the YAML DSL support for parsing Kamelets
 */
class KameletYamlRoutes extends YamlRoutesBuilderLoaderSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KameletYamlRoutes.class);
    private CamelContext camelContext;
    private final String cp = System.getProperty("java.class.path");
    private final Set<String> downloaded = new HashSet<>();

    public KameletYamlRoutes(String extension) {
        super(extension);
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
    protected RouteBuilder builder(Node node, Resource resource) {
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

        downloadDependencies(dependencies);

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
                gav = "org.apache.camel:camel-" + dep.substring(6) + ":" + camelContext.getVersion();
            }
            if (isValidGav(gav)) {
                gavs.add(gav);
            }
        }

        if (!gavs.isEmpty()) {
            for (String gav : gavs) {
                MavenGav mg = MavenGav.parseGav(gav);
                if (mg.getVersion() == null) {
                    mg.setVersion(camelContext.getVersion());
                }
                DownloaderHelper.downloadDependency(camelContext, mg.getGroupId(), mg.getArtifactId(), mg.getVersion());
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

        MavenGav mg = MavenGav.parseGav(gav);
        boolean exists = DownloaderHelper.alreadyOnClasspath(camelContext, mg.getArtifactId());
        // valid if not already on classpath
        return !exists;
    }

}
