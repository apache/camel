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

import java.util.Map;

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.exception.UnsupportedFieldException;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.util.URISupport;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

@YamlType(
          inline = true,
          types = org.apache.camel.model.KameletDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = "kamelet",
          properties = {
                  @YamlProperty(name = "inherit-error-handler", type = "boolean"),
                  @YamlProperty(name = "name", type = "string", required = true),
                  @YamlProperty(name = "parameters", type = "object"),
                  @YamlProperty(name = "steps", type = "array:org.apache.camel.model.ProcessorDefinition")
          })
public class KameletDeserializer extends YamlDeserializerBase<KameletDefinition> {
    public KameletDeserializer() {
        super(KameletDefinition.class);
    }

    @Override
    protected KameletDefinition newInstance() {
        return new KameletDefinition();
    }

    @Override
    protected KameletDefinition newInstance(String value) {
        return new KameletDefinition(value);
    }

    @Override
    protected void setProperties(KameletDefinition target, MappingNode node) {
        final YamlDeserializationContext dc = getDeserializationContext(node);

        String name = null;
        Map<String, Object> parameters = null;

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
                case "name":
                    name = asText(val);
                    break;
                case "inheritErrorHandler":
                case "inherit-error-handler":
                    target.setInheritErrorHandler(asBoolean(val));
                    break;
                case "parameters":
                    parameters = asScalarMap(tuple.getValueNode());
                    break;
                default:
                    throw new UnsupportedFieldException(node, key);
            }
        }

        if (parameters != null) {
            name += "?" + URISupport.createQueryString(parameters, false);
        }

        target.setName(name);
    }
}
