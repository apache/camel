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
package org.apache.camel.dsl.yaml.deserializers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.YamlSupport;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlType(
          nodes = "beans",
          order = YamlDeserializerResolver.ORDER_DEFAULT)
public class BeansDeserializer extends YamlDeserializerSupport implements ConstructNode {
    @Override
    public Object construct(Node node) {
        final SequenceNode sn = asSequenceNode(node);
        final List<CamelContextCustomizer> customizers = new ArrayList<>();

        for (Node item : sn.getValue()) {
            customizers.add(createCustomizer(item));
        }

        return YamlSupport.customizer(customizers);
    }

    private static CamelContextCustomizer createCustomizer(Node node) {
        final MappingNode bn = asMappingNode(node);

        String name = null;
        String type = null;
        Map<String, Object> properties = null;

        for (NodeTuple tuple : bn.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            switch (key) {
                case "name":
                    name = asText(val);
                    break;
                case "type":
                    type = asText(val);
                    if (!type.startsWith("#class:")) {
                        type = "#class:" + type;
                    }
                    break;
                case "properties":
                    properties = asMap(val);
                    break;
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        ObjectHelper.notNull(name, "The bean name must be set");
        ObjectHelper.notNull(type, "The bean type must be set");

        return createCustomizer(name, type, properties);
    }

    private static CamelContextCustomizer createCustomizer(String name, String type, Map<String, Object> properties) {
        return new CamelContextCustomizer() {
            @Override
            public void configure(CamelContext camelContext) {
                try {
                    Object target = PropertyBindingSupport.resolveBean(camelContext, type);

                    if (properties != null && !properties.isEmpty()) {
                        YamlSupport.setPropertiesOnTarget(camelContext, target, properties);
                    }

                    camelContext.getRegistry().bind(name, target);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
