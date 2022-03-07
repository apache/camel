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
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlType(
          inline = true,
          types = org.apache.camel.model.RouteConfigurationDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = { "route-configuration", "routeConfiguration" },
          properties = {
                  @YamlProperty(name = "id", type = "string"),
                  @YamlProperty(name = "precondition", type = "string"),
                  @YamlProperty(name = "intercept", type = "array:org.apache.camel.model.InterceptDefinition"),
                  @YamlProperty(name = "intercept-from", type = "array:org.apache.camel.model.InterceptFromDefinition"),
                  @YamlProperty(name = "intercept-send-to-endpoint",
                                type = "array:org.apache.camel.model.InterceptSendToEndpointDefinition"),
                  @YamlProperty(name = "on-completion", type = "array:org.apache.camel.model.OnCompletionDefinition"),
                  @YamlProperty(name = "on-exception", type = "array:org.apache.camel.model.OnExceptionDefinition")
          })
public class RouteConfigurationDefinitionDeserializer extends YamlDeserializerBase<RouteConfigurationDefinition> {
    public RouteConfigurationDefinitionDeserializer() {
        super(RouteConfigurationDefinition.class);
    }

    @Override
    protected RouteConfigurationDefinition newInstance() {
        return new RouteConfigurationDefinition();
    }

    @Override
    public Object construct(Node node) {
        final RouteConfigurationDefinition target = newInstance();

        final YamlDeserializationContext dc = getDeserializationContext(node);
        final SequenceNode sn = asSequenceNode(node);
        for (Node item : sn.getValue()) {
            final MappingNode bn = asMappingNode(item);
            setDeserializationContext(item, dc);

            for (NodeTuple tuple : bn.getValue()) {
                final String key = asText(tuple.getKeyNode());
                final Node val = tuple.getValueNode();
                switch (key) {
                    case "id": {
                        target.setId(asText(val));
                        break;
                    }
                    case "precondition":
                        target.setPrecondition(asText(val));
                        break;
                    case "on-exception":
                        setDeserializationContext(val, dc);
                        OnExceptionDefinition oed = asType(val, OnExceptionDefinition.class);
                        target.getOnExceptions().add(oed);
                        break;
                    case "on-completion":
                        setDeserializationContext(val, dc);
                        OnCompletionDefinition ocd = asType(val, OnCompletionDefinition.class);
                        target.getOnCompletions().add(ocd);
                        break;
                    case "intercept":
                        setDeserializationContext(val, dc);
                        InterceptDefinition id = asType(val, InterceptDefinition.class);
                        target.getIntercepts().add(id);
                        break;
                    case "intercept-from":
                        setDeserializationContext(val, dc);
                        InterceptFromDefinition ifd = asType(val, InterceptFromDefinition.class);
                        target.getInterceptFroms().add(ifd);
                        break;
                    case "intercept-send-to-endpoint":
                        setDeserializationContext(val, dc);
                        InterceptSendToEndpointDefinition isted = asType(val, InterceptSendToEndpointDefinition.class);
                        target.getInterceptSendTos().add(isted);
                        break;
                    default:
                        throw new UnsupportedFieldException(val, key);
                }
            }
        }

        return target;
    }

}
