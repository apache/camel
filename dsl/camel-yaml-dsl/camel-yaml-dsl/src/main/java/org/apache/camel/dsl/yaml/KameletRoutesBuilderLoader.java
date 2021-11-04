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
import java.util.Set;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asMappingNode;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asStringSet;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

@ManagedResource(description = "Managed Kamelet RoutesBuilderLoader")
@RoutesLoader(KameletRoutesBuilderLoader.EXTENSION)
public class KameletRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {
    public static final String EXTENSION = "kamelet.yaml";

    public KameletRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected RouteBuilder builder(Node node) {
        Node template = nodeAt(node, "/spec/template");
        if (template == null) {
            // fallback till flows get removed
            template = nodeAt(node, "/spec/flows");
        }
        if (template == null) {
            // fallback till flow get removed
            template = nodeAt(node, "/spec/flow");
        }
        if (template == null) {
            throw new IllegalArgumentException("No template defined");
        }

        Set<String> required = asStringSet(nodeAt(node, "/spec/definition/required"));
        if (required == null) {
            required = Collections.emptySet();
        }

        final YamlDeserializationContext context = this.getDeserializationContext();
        final RouteTemplateDefinition rtd = context.construct(template, RouteTemplateDefinition.class);

        rtd.id(asText(nodeAt(node, "/metadata/name")));

        Node properties = nodeAt(node, "/spec/definition/properties");
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

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getRouteTemplateCollection().routeTemplate(rtd);
            }
        };
    }
}
