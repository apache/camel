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

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

@YamlType(
          types = NamedBeanDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "name", type = "string", required = true),
                  @YamlProperty(name = "type", type = "string", required = true),
                  @YamlProperty(name = "properties", type = "object")
          })
public class NamedBeanDeserializer extends YamlDeserializerBase<NamedBeanDefinition> {

    public NamedBeanDeserializer() {
        super(NamedBeanDefinition.class);
    }

    @Override
    protected NamedBeanDefinition newInstance() {
        return new NamedBeanDefinition();
    }

    @Override
    public NamedBeanDefinition construct(Node node) {
        final MappingNode bn = asMappingNode(node);
        final NamedBeanDefinition answer = new NamedBeanDefinition();

        for (NodeTuple tuple : bn.getValue()) {
            final String key = asText(tuple.getKeyNode());
            final Node val = tuple.getValueNode();

            switch (key) {
                case "name":
                    answer.setName(asText(val));
                    break;
                case "type":
                    String type = asText(val);
                    if (!type.startsWith("#class:")) {
                        type = "#class:" + type;
                    }
                    answer.setType(type);
                    break;
                case "properties":
                    answer.setProperties(asMap(val));
                    break;
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        ObjectHelper.notNull(answer.getName(), "The bean name must be set");
        ObjectHelper.notNull(answer.getType(), "The bean type must be set");

        return answer;
    }
}
