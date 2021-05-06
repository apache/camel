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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplateParameterDefinition;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlIn
@YamlType(
          nodes = { "route-template", "template" },
          types = org.apache.camel.model.RouteTemplateDefinition.class,
          order = org.apache.camel.dsl.yaml.common.YamlDeserializerResolver.ORDER_LOWEST - 1,
          properties = {
                  @YamlProperty(name = "id",
                                type = "string",
                                required = true),
                  @YamlProperty(name = "from",
                                type = "object:org.apache.camel.model.FromDefinition",
                                required = true),
                  @YamlProperty(name = "parameters",
                                type = "array:org.apache.camel.model.RouteTemplateParameterDefinition"),
                  @YamlProperty(name = "beans",
                                type = "array:org.apache.camel.dsl.yaml.deserializers.NamedBeanDefinition")
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
            case "from": {
                OutputAwareFromDefinition val = asType(node, OutputAwareFromDefinition.class);
                RouteDefinition route = new RouteDefinition();
                route.setInput(val.getDelegate());
                route.setOutputs(val.getOutputs());
                target.setRoute(route);
                break;
            }
            case "parameters": {
                List<RouteTemplateParameterDefinition> val = asFlatList(node, RouteTemplateParameterDefinition.class);
                target.setTemplateParameters(val);
                break;
            }
            case "beans": {
                final SequenceNode sn = asSequenceNode(node);
                final List<NamedBeanDefinition> beans = new ArrayList<>();
                final YamlDeserializationContext dc = getDeserializationContext(node);

                asFlatCollection(node, NamedBeanDefinition.class, beans);

                target.configure(new Consumer<RouteTemplateContext>() {
                    @Override
                    public void accept(RouteTemplateContext context) {
                        for (int i = 0; i < beans.size(); i++) {
                            NamedBeanDefinition bean = beans.get(i);

                            ObjectHelper.notNull(bean.getName(), "The bean name must be set");
                            ObjectHelper.notNull(bean.getType(), "The bean type must be set");

                            try {
                                context.bind(
                                        bean.getName(),
                                        bean.newInstance(context.getCamelContext()));
                            } catch (Exception e) {
                                throw new RuntimeCamelException(e);
                            }
                        }
                    }
                });
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }
}
