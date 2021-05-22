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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.YamlRoutesBuilderLoaderSupport;
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
public class KameletDependencyDownloader extends YamlRoutesBuilderLoaderSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(KameletDependencyDownloader.class);
    private CamelContext camelContext;
    private final String cp = System.getProperty("java.class.path");
    private final Set<String> downloaded = new HashSet<>();

    public KameletDependencyDownloader(String extension) {
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
    protected RouteBuilder builder(Node node) {
        final List<String> dependencies = new ArrayList<>();

        Node deps = nodeAt(node, "/spec/dependencies");
        if (deps.getNodeType() == NodeType.SEQUENCE) {
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
                // its a known camel component
                gav = "org.apache.camel:camel-" + dep.substring(6) + ":" + camelContext.getVersion();
            }
            if (isValidGav(gav)) {
                gavs.add(gav);
            }
        }

        for (String gav : gavs) {
            LOG.debug("Downloading dependency: {}", gav);
            // TODO: download dependency and add to classpath
        }
    }

    private boolean isValidGav(String gav) {
        if (downloaded.contains(gav)) {
            // already downloaded
            return false;
        }

        // skip camel-core and camel-kamelet as they are already included
        if (gav.startsWith("org.apache.camel:camel-core") || gav.startsWith("org.apache.camel:camel-kamelet:")) {
            return false;
        }

        String[] arr = gav.split(":");
        String name = null;
        if (arr.length == 3) {
            String aid = arr[1];
            String v = arr[2];
            name = aid + "-" + v + ".jar";
        }

        if (name != null && cp != null) {
            // is it already on classpath
            if (cp.contains(name)) {
                // already on classpath
                return false;
            }
        }

        if (name != null && camelContext.getApplicationContextClassLoader() != null) {
            ClassLoader cl = camelContext.getApplicationContextClassLoader();
            if (cl instanceof URLClassLoader) {
                URLClassLoader ucl = (URLClassLoader) cl;
                for (URL u : ucl.getURLs()) {
                    String s = u.toString();
                    if (s.contains(name)) {
                        // already on classpath
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
