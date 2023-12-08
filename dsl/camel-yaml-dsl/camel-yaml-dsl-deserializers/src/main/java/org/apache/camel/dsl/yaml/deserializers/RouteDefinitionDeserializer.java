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
                  @YamlProperty(name = "nodePrefixId", type = "string"),
                  @YamlProperty(name = "precondition", type = "string"),
                  @YamlProperty(name = "routeConfigurationId", type = "string"),
                  @YamlProperty(name = "autoStartup", type = "boolean"),
                  @YamlProperty(name = "routePolicy", type = "string"),
                  @YamlProperty(name = "startupOrder", type = "number"),
                  @YamlProperty(name = "streamCaching", type = "boolean"),
                  @YamlProperty(name = "messageHistory", type = "boolean"),
                  @YamlProperty(name = "logMask", type = "boolean"),
                  @YamlProperty(name = "trace", type = "boolean"),
                  @YamlProperty(name = "shutdownRoute", type = "enum:Default,Defer",
                                defaultValue = "Default",
                                description = "To control how to shut down the route."),
                  @YamlProperty(name = "shutdownRunningTask", type = "enum:CompleteCurrentTaskOnly,CompleteAllTasks",
                                defaultValue = "CompleteCurrentTaskOnly",
                                description = "To control how to shut down the route."),
                  @YamlProperty(name = "inputType", type = "object:org.apache.camel.model.InputTypeDefinition"),
                  @YamlProperty(name = "outputType", type = "object:org.apache.camel.model.OutputTypeDefinition"),
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
            String key = asText(tuple.getKeyNode());
            Node val = tuple.getValueNode();

            setDeserializationContext(val, dc);

            key = org.apache.camel.util.StringHelper.dashToCamelCase(key);
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
                    target.setNodePrefixId(asText(val));
                    break;
                case "routeConfigurationId":
                    target.setRouteConfigurationId(asText(val));
                    break;
                case "autoStartup":
                    target.setAutoStartup(asText(val));
                    break;
                case "routePolicy":
                    target.setRoutePolicyRef(asText(val));
                    break;
                case "startupOrder":
                    target.setStartupOrder(asInt(val));
                    break;
                case "streamCaching":
                    target.setStreamCache(asText(val));
                    break;
                case "logMask":
                    target.setLogMask(asText(val));
                    break;
                case "messageHistory":
                    target.setMessageHistory(asText(val));
                    break;
                case "shutdownRoute":
                    target.setShutdownRoute(asText(val));
                    break;
                case "shutdownRunningTask":
                    target.setShutdownRunningTask(asText(val));
                    break;
                case "trace":
                    target.setTrace(asText(val));
                    break;
                case "inputType":
                    target.setInputType(asType(val, InputTypeDefinition.class));
                    break;
                case "outputType":
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
