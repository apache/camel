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
package org.apache.camel.dsl.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asStringSet;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.setDeserializationContext;

@ManagedResource(description = "Managed Kamelet RoutesBuilderLoader")
@RoutesLoader(KameletRoutesBuilderLoader.EXTENSION)
public class KameletRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {

    public static final String EXTENSION = "kamelet.yaml";

    public KameletRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected RouteBuilder builder(final YamlDeserializationContext ctx, final Node root) {
        setDeserializationContext(root, ctx);

        Node template = nodeAt(root, "/spec/template");
        if (template == null) {
            throw new IllegalArgumentException("No template defined");
        }

        Set<String> required = asStringSet(nodeAt(root, "/spec/definition/required"));
        if (required == null) {
            required = Collections.emptySet();
        }

        final RouteTemplateDefinition rtd = ctx.construct(template, RouteTemplateDefinition.class);
        rtd.id(asText(nodeAt(root, "/metadata/name")));

        Node properties = nodeAt(root, "/spec/definition/properties");
        if (properties != null) {
            rtd.setTemplateParameters(new ArrayList<>());
            for (NodeTuple p : asMappingNode(properties).getValue()) {
                final String key = asText(p.getKeyNode());
                final Node def = nodeAt(p.getValueNode(), "/default");

                RouteTemplateParameterDefinition rtpd = new RouteTemplateParameterDefinition();
                rtpd.setName(key);
                rtpd.setDefaultValue(asText(def));
                rtpd.setRequired(required.contains(key));

                rtd.getTemplateParameters().add(rtpd);
            }
        }

        // if there are dependencies then we should include them
        Node deps = nodeAt(root, "/spec/dependencies");
        CamelContextCustomizer customizer = null;
        if (deps != null) {
            customizer = preConfigureDependencies(deps);
        }
        final CamelContextCustomizer dependencies = customizer;

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                if (dependencies != null) {
                    dependencies.configure(getCamelContext());
                }
                getRouteTemplateCollection().routeTemplate(rtd);
            }
        };
    }

    private CamelContextCustomizer preConfigureDependencies(Node node) {
        final List<String> dep = YamlDeserializerSupport.asStringList(node);
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                // notify the listeners about each dependency detected
                for (DependencyStrategy ds : camelContext.getRegistry().findByType(DependencyStrategy.class)) {
                    for (String d : dep) {
                        try {
                            ds.onDependency(d);
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
        };
    }

}
