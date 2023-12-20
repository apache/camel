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

import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.RouteTemplateBeanDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;

/**
 * The YAML deserializer for the bean factory used by the route template.
 */
@YamlType(
          inline = false,
          types = org.apache.camel.model.RouteTemplateBeanDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = { "template-bean", "templateBean" },
          properties = {
                  @YamlProperty(name = "name", type = "string", required = true),
                  @YamlProperty(name = "type", type = "string", required = true),
                  @YamlProperty(name = "property", type = "array:org.apache.camel.model.PropertyDefinition"),
                  @YamlProperty(name = "properties", type = "object"),
                  @YamlProperty(name = "scriptLanguage", type = "string"),
                  @YamlProperty(name = "script", type = "string")
          })
public class RouteTemplateBeanDefinitionDeserializer extends BeanFactoryDefinitionDeserializer<RouteTemplateBeanDefinition> {

    public RouteTemplateBeanDefinitionDeserializer() {
        super(RouteTemplateBeanDefinition.class);
    }

    @Override
    protected RouteTemplateBeanDefinition newInstance() {
        return new RouteTemplateBeanDefinition();
    }

    @Override
    protected RouteTemplateBeanDefinition newInstance(String value) {
        RouteTemplateBeanDefinition answer = new RouteTemplateBeanDefinition();
        answer.setName(value);
        return answer;
    }
}
