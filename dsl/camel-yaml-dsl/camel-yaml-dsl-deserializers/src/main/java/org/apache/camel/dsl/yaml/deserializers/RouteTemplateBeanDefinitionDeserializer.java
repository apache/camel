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

import java.util.stream.Collectors;

import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.RouteTemplateBeanDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.Node;

@YamlType(
          inline = true,
          types = org.apache.camel.model.RouteTemplateBeanDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = { "template-bean", "templateBean" },
          properties = {
                  @YamlProperty(name = "bean-type", type = "string"),
                  @YamlProperty(name = "name", type = "string", required = true),
                  @YamlProperty(name = "property", type = "array:org.apache.camel.model.PropertyDefinition"),
                  @YamlProperty(name = "properties", type = "object"),
                  @YamlProperty(name = "script", type = "object:org.apache.camel.model.RouteTemplateScriptDefinition"),
                  @YamlProperty(name = "type", type = "string", required = true)
          })
public class RouteTemplateBeanDefinitionDeserializer extends YamlDeserializerBase<RouteTemplateBeanDefinition> {
    public RouteTemplateBeanDefinitionDeserializer() {
        super(RouteTemplateBeanDefinition.class);
    }

    @Override
    protected RouteTemplateBeanDefinition newInstance() {
        return new RouteTemplateBeanDefinition();
    }

    @Override
    protected RouteTemplateBeanDefinition newInstance(String value) {
        return new RouteTemplateBeanDefinition(value);
    }

    @Override
    protected boolean setProperty(
            RouteTemplateBeanDefinition target, String propertyKey,
            String propertyName, Node node) {
        switch (propertyKey) {
            case "bean-type": {
                String val = asText(node);
                target.setBeanType(val);
                break;
            }
            case "name": {
                String val = asText(node);
                target.setName(val);
                break;
            }
            case "property": {
                java.util.List<org.apache.camel.model.PropertyDefinition> val
                        = asFlatList(node, org.apache.camel.model.PropertyDefinition.class);
                target.setProperties(val);
                break;
            }
            case "properties": {
                target.setProperties(
                        asMap(node).entrySet().stream()
                                .map(e -> new PropertyDefinition(e.getKey(), (String) e.getValue()))
                                .collect(Collectors.toList()));
                break;
            }
            case "script": {
                org.apache.camel.model.RouteTemplateScriptDefinition val
                        = asType(node, org.apache.camel.model.RouteTemplateScriptDefinition.class);
                target.setScript(val);
                break;
            }
            case "type": {
                String val = asText(node);
                target.setType(val);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }
}
