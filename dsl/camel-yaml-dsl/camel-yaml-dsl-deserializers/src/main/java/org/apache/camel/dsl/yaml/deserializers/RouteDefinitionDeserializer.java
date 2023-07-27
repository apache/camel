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
import org.apache.camel.model.InputTypeDefinition;
import org.apache.camel.model.OutputTypeDefinition;
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
                  @YamlProperty(name = "description", type = "string"),
                  @YamlProperty(name = "group", type = "string"),
                  @YamlProperty(name = "node-prefix-id", type = "string"),
                  @YamlProperty(name = "precondition", type = "string"),
                  @YamlProperty(name = "route-configuration-id", type = "string"),
                  @YamlProperty(name = "auto-startup", type = "boolean"),
                  @YamlProperty(name = "route-policy", type = "string"),
                  @YamlProperty(name = "startup-order", type = "number"),
                  @YamlProperty(name = "stream-caching", type = "boolean"),
                  @YamlProperty(name = "message-history", type = "boolean"),
                  @YamlProperty(name = "log-mask", type = "boolean"),
                  @YamlProperty(name = "trace", type = "boolean"),
                  @YamlProperty(name = "input-type", type = "object:org.apache.camel.model.InputTypeDefinition"),
                  @YamlProperty(name = "output-type", type = "object:org.apache.camel.model.OutputTypeDefinition"),
                  @YamlProperty(name = "from", type = "object:org.apache.camel.model.FromDefinition", required = true)
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
                case "id":
                    target.setId(asText(val));
                    break;
                case "description":
                    target.setDescription(asText(val));
                    break;
                case "precondition":
                    target.setPrecondition(asText(val));
                    break;
                case "group":
                    target.setGroup(asText(val));
                    break;
                case "nodePrefixId":
                case "node-prefix-id":
                    target.setNodePrefixId(asText(val));
                    break;
                case "routeConfigurationId":
                case "route-configuration-id":
                    target.setRouteConfigurationId(asText(val));
                    break;
                case "autoStartup":
                case "auto-startup":
                    target.setAutoStartup(asText(val));
                    break;
                case "routePolicy":
                case "route-policy":
                    target.setRoutePolicyRef(asText(val));
                    break;
                case "startupOrder":
                case "startup-order":
                    target.setStartupOrder(asInt(val));
                    break;
                case "streamCaching":
                case "stream-caching":
                    target.setStreamCache(asText(val));
                    break;
                case "logMask":
                case "log-mask":
                    target.setLogMask(asText(val));
                    break;
                case "messageHistory":
                case "message-history":
                    target.setMessageHistory(asText(val));
                    break;
                case "trace":
                    target.setTrace(asText(val));
                    break;
                case "inputType":
                case "input-type":
                    target.setInputType(asType(val, InputTypeDefinition.class));
                    break;
                case "outputType":
                case "output-type":
                    target.setOutputType(asType(val, OutputTypeDefinition.class));
                    break;
                case "from":
                    val.setProperty(RouteDefinition.class.getName(), target);
                    target.setInput(asType(val, FromDefinition.class));
                    break;
                default:
                    throw new UnsupportedFieldException(node, key);
            }
        }
    }

}
