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

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.dsl.yaml.common.YamlSupport;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlIn
@YamlType(
          nodes = "beans",
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          properties = {
                  @YamlProperty(name = "__extends",
                                type = "array:org.apache.camel.model.app.RegistryBeanDefinition")
          })
public class BeansDeserializer extends YamlDeserializerSupport implements ConstructNode {
    @Override
    public Object construct(Node node) {
        final SequenceNode sn = asSequenceNode(node);
        final List<CamelContextCustomizer> customizers = new ArrayList<>();
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (Node item : sn.getValue()) {
            setDeserializationContext(item, dc);

            RegistryBeanDefinition bean = asType(item, RegistryBeanDefinition.class);

            ObjectHelper.notNull(bean.getName(), "The bean name must be set");
            ObjectHelper.notNull(bean.getType(), "The bean type must be set");
            if (!bean.getType().startsWith("#class:")) {
                bean.setType("#class:" + bean.getType());
            }

            customizers.add(new CamelContextCustomizer() {
                @Override
                public void configure(CamelContext camelContext) {
                    try {
                        // to support hot reloading of beans then we need to unbind old existing first
                        String name = bean.getName();
                        camelContext.getRegistry().unbind(name);
                        camelContext.getRegistry().bind(
                                name,
                                newInstance(bean, camelContext));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        return YamlSupport.customizer(customizers);
    }

    public Object newInstance(RegistryBeanDefinition bean, CamelContext context) throws Exception {
        final Object target = PropertyBindingSupport.resolveBean(context, bean.getType());

        if (bean.getProperties() != null && !bean.getProperties().isEmpty()) {
            PropertyBindingSupport.setPropertiesOnTarget(context, target, bean.getProperties());
        }

        return target;
    }

}
