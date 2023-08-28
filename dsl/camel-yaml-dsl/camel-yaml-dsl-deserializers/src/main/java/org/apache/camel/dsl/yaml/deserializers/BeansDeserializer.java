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
import org.apache.camel.model.Model;
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
        final BeansCustomizer answer = new BeansCustomizer();
        final SequenceNode sn = asSequenceNode(node);
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (Node item : sn.getValue()) {
            setDeserializationContext(item, dc);

            RegistryBeanDefinition bean = asType(item, RegistryBeanDefinition.class);
            if (dc != null) {
                bean.setResource(dc.getResource());
            }

            ObjectHelper.notNull(bean.getName(), "The bean name must be set");
            ObjectHelper.notNull(bean.getType(), "The bean type must be set");
            if (!bean.getType().startsWith("#class:")) {
                bean.setType("#class:" + bean.getType());
            }

            answer.addBean(bean);
        }

        return answer;
    }

    public Object newInstance(RegistryBeanDefinition bean, CamelContext context) throws Exception {
        final Object target = PropertyBindingSupport.resolveBean(context, bean.getType());

        if (bean.getProperties() != null && !bean.getProperties().isEmpty()) {
            PropertyBindingSupport.setPropertiesOnTarget(context, target, bean.getProperties());
        }

        return target;
    }

    protected void registerBean(
            CamelContext camelContext,
            List<RegistryBeanDefinition> delayedRegistrations,
            RegistryBeanDefinition def, boolean delayIfFailed) {
        try {
            // to support hot reloading of beans then we need to unbind old existing first
            String name = def.getName();
            Object bean = newInstance(def, camelContext);
            camelContext.getRegistry().unbind(name);
            camelContext.getRegistry().bind(name, bean);

            // register bean in model
            Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
            model.addRegistryBean(def);

        } catch (Exception e) {
            if (delayIfFailed) {
                delayedRegistrations.add(def);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private class BeansCustomizer implements CamelContextCustomizer {

        private final List<RegistryBeanDefinition> delayedRegistrations = new ArrayList<>();
        private final List<RegistryBeanDefinition> beans = new ArrayList<>();

        public void addBean(RegistryBeanDefinition bean) {
            beans.add(bean);
        }

        @Override
        public void configure(CamelContext camelContext) {
            // first-pass of creating beans
            for (RegistryBeanDefinition bean : beans) {
                registerBean(camelContext, delayedRegistrations, bean, true);
            }
            beans.clear();
            // second-pass of creating beans should fail if not possible
            for (RegistryBeanDefinition bean : delayedRegistrations) {
                registerBean(camelContext, delayedRegistrations, bean, false);
            }
            delayedRegistrations.clear();
        }
    }

}
