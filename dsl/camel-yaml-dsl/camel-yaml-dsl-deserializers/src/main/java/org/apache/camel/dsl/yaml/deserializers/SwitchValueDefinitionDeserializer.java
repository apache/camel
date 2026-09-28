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

import java.math.BigDecimal;

import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.model.SwitchValueDefinition;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

@YamlType(types = SwitchValueDefinition.class, order = YamlDeserializerResolver.ORDER_DEFAULT)
public class SwitchValueDefinitionDeserializer extends YamlDeserializerSupport implements ConstructNode {
    @Override
    public Object construct(Node node) {
        if (!(node instanceof MappingNode mapping) || mapping.getValue().size() != 1) {
            throw new YamlDeserializationException(node, "Each switch value must be a single-entry mapping");
        }
        NodeTuple entry = mapping.getValue().get(0);
        if (!(entry.getKeyNode() instanceof ScalarNode key) || !Tag.STR.equals(key.getTag())) {
            throw new YamlDeserializationException(node, "Switch value names must be strings");
        }
        if (!(entry.getValueNode() instanceof ScalarNode value)) {
            throw new YamlDeserializationException(node, "Switch values must be scalar literals");
        }
        Object literal;
        if (Tag.STR.equals(value.getTag())) {
            literal = value.getValue();
        } else if (Tag.BOOL.equals(value.getTag())) {
            literal = Boolean.valueOf(value.getValue());
        } else if (Tag.INT.equals(value.getTag()) || Tag.FLOAT.equals(value.getTag())) {
            try {
                literal = new BigDecimal(value.getValue());
            } catch (NumberFormatException e) {
                throw new YamlDeserializationException(node, "Switch numbers must be finite decimal literals", e);
            }
        } else {
            throw new YamlDeserializationException(node, "Switch values must be strings, booleans or numbers");
        }
        return new SwitchValueDefinition(key.getValue(), literal);
    }
}
