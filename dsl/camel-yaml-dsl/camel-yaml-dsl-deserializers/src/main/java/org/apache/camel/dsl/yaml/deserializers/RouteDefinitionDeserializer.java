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

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

@YamlIn
@YamlType(
          nodes = "route",
          types = RouteDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "id", type = "string"),
                  @YamlProperty(name = "group", type = "string"),
                  @YamlProperty(name = "from", type = "object:org.apache.camel.model.FromDefinition", required = true),
                  @YamlProperty(name = "steps", type = "array:org.apache.camel.model.ProcessorDefinition", required = true)
          })
public class RouteDefinitionDeserializer extends YamlDeserializerBase<RouteDefinition> {
    public RouteDefinitionDeserializer() {
        super(RouteDefinition.class);
    }

    @Override
    protected RouteDefinition newInstance() {
        return new RouteDefinition();
    }

    @Override
    protected void setProperties(RouteDefinition target, MappingNode node) {
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (NodeTuple tuple : node.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            switch (key) {
                case "steps":
                    setSteps(target, val);
                    break;
                case "id":
                    target.setId(asText(val));
                    break;
                case "group":
                    target.setGroup(asText(val));
                    break;
                case "from":
                    target.setInput(asType(val, FromDefinition.class));
                    break;
                default:
                    throw new UnsupportedFieldException(node, key);
            }
        }
    }

}
