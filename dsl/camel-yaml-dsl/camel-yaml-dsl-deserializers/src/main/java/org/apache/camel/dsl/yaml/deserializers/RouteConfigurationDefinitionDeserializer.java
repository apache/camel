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
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

@YamlIn
@YamlType(
          inline = false,
          types = org.apache.camel.model.RouteConfigurationDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = { "route-configuration", "routeConfiguration" },
          properties = {
                  @YamlProperty(name = "id", type = "string"),
                  @YamlProperty(name = "description", type = "string"),
                  @YamlProperty(name = "precondition", type = "string"),
                  @YamlProperty(name = "errorHandler", type = "object:org.apache.camel.model.ErrorHandlerDefinition"),
                  @YamlProperty(name = "intercept", wrapItem = true,
                                type = "array:org.apache.camel.model.InterceptDefinition"),
                  @YamlProperty(name = "interceptFrom", wrapItem = true,
                                type = "array:org.apache.camel.model.InterceptFromDefinition"),
                  @YamlProperty(name = "interceptSendToEndpoint", wrapItem = true,
                                type = "array:org.apache.camel.model.InterceptSendToEndpointDefinition"),
                  @YamlProperty(name = "onCompletion", wrapItem = true,
                                type = "array:org.apache.camel.model.OnCompletionDefinition"),
                  @YamlProperty(name = "onException", wrapItem = true,
                                type = "array:org.apache.camel.model.OnExceptionDefinition")
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
        final MappingNode bn = asMappingNode(node);
        setDeserializationContext(node, dc);

        for (NodeTuple tuple : bn.getValue()) {
            String key = asText(tuple.getKeyNode());
            Node val = tuple.getValueNode();

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
                case "errorHandler":
                    setDeserializationContext(val, dc);
                    ErrorHandlerDefinition ehd = asType(val, ErrorHandlerDefinition.class);
                    target.setErrorHandler(ehd);
                    break;
                case "onException":
                    setDeserializationContext(val, dc);
                    target.setOnExceptions(asList(val, OnExceptionDefinition.class));
                    break;
                case "onCompletion":
                    setDeserializationContext(val, dc);
                    target.setOnCompletions(asList(val, OnCompletionDefinition.class));
                    break;
                case "intercept":
                    setDeserializationContext(val, dc);
                    target.setIntercepts(asList(val, InterceptDefinition.class));
                    break;
                case "interceptFrom":
                    setDeserializationContext(val, dc);
                    target.setInterceptFroms(asList(val, InterceptFromDefinition.class));
                    break;
                case "interceptSendToEndpoint":
                    setDeserializationContext(val, dc);
                    target.setInterceptSendTos(asList(val, InterceptSendToEndpointDefinition.class));
                    break;
                default:
                    throw new UnsupportedFieldException(val, key);
            }
        }

        return target;
    }

}
