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

import java.util.List;

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.exception.InvalidRouteException;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateBeanDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.Node;

@YamlIn
@YamlType(
          nodes = { "route-template", "routeTemplate" },
          types = org.apache.camel.model.RouteTemplateDefinition.class,
          order = org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1,
          properties = {
                  @YamlProperty(name = "id",
                                type = "string",
                                required = true),
                  @YamlProperty(name = "description", type = "string"),
                  @YamlProperty(name = "route",
                                type = "object:org.apache.camel.model.RouteDefinition"),
                  @YamlProperty(name = "from",
                                type = "object:org.apache.camel.model.FromDefinition"),
                  @YamlProperty(name = "parameters",
                                type = "array:org.apache.camel.model.RouteTemplateParameterDefinition"),
                  @YamlProperty(name = "beans",
                                type = "array:org.apache.camel.model.RouteTemplateBeanDefinition")
          })
public class RouteTemplateDefinitionDeserializer extends YamlDeserializerBase<RouteTemplateDefinition> {
    public RouteTemplateDefinitionDeserializer() {
        super(RouteTemplateDefinition.class);
    }

    @Override
    protected RouteTemplateDefinition newInstance() {
        return new RouteTemplateDefinition();
    }

    @Override
    protected boolean setProperty(
            RouteTemplateDefinition target, String propertyKey, String propertyName, Node node) {

        switch (propertyKey) {
            case "id": {
                target.setId(asText(node));
                break;
            }
            case "description": {
                target.setDescription(asText(node));
                break;
            }
            case "route": {
                RouteDefinition route = asType(node, RouteDefinition.class);
                target.setRoute(route);
                break;
            }
            case "from": {
                OutputAwareFromDefinition val = asType(node, OutputAwareFromDefinition.class);
                RouteDefinition route = new RouteDefinition();
                route.setInput(val.getDelegate());
                route.setOutputs(val.getOutputs());
                target.setRoute(route);
                break;
            }
            case "parameters": {
                List<RouteTemplateParameterDefinition> items = asFlatList(node, RouteTemplateParameterDefinition.class);
                target.setTemplateParameters(items);
                break;
            }
            case "beans": {
                List<RouteTemplateBeanDefinition> items = asFlatList(node, RouteTemplateBeanDefinition.class);
                target.setTemplateBeans(items);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void afterPropertiesSet(RouteTemplateDefinition target, Node node) {
        // either from or route must be set
        if (target.getRoute() == null) {
            throw new InvalidRouteException(node, "RouteTemplate must have route or from set");
        }
        if (target.getRoute().getInput() == null) {
            throw new InvalidRouteException(node, "RouteTemplate must have from set");
        }
    }
}
