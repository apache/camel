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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.BeanModelHelper;
import org.apache.camel.model.Model;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PojoBeanHelper;
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
                                type = "array:org.apache.camel.model.BeanFactoryDefinition")
          })
public class BeansDeserializer extends YamlDeserializerSupport implements ConstructNode {

    private final Set<String> beanCache = new HashSet<>();

    @Override
    public Object construct(Node node) {
        final BeansCustomizer answer = new BeansCustomizer();
        final SequenceNode sn = asSequenceNode(node);
        final YamlDeserializationContext dc = getDeserializationContext(node);

        for (Node item : sn.getValue()) {
            setDeserializationContext(item, dc);

            BeanFactoryDefinition<?> bean = asType(item, BeanFactoryDefinition.class);
            if (dc != null) {
                bean.setResource(dc.getResource());
            }

            ObjectHelper.notNull(bean.getName(), "The bean name must be set");
            if (bean.getScriptLanguage() != null || bean.getScript() != null) {
                ObjectHelper.notNull(bean.getScriptLanguage(), "The bean script language must be set");
                ObjectHelper.notNull(bean.getScript(), "The bean script must be set");
            }
            boolean script = bean.getScriptLanguage() != null && bean.getScript() != null;
            // the type (class name) is optional for a bean created by a script or a builder
            if (!script && bean.getBuilderClass() == null) {
                ObjectHelper.notNull(bean.getType(), "The bean type must be set");
            }
            if (bean.getType() != null && !bean.getType().startsWith("#class:")) {
                bean.setType("#class:" + bean.getType());
            }

            // due to yaml-dsl is pre parsing beans which gets created eager
            // and then later beans can be parsed again such as from Yaml dsl files
            // we need to avoid double creating beans and therefore has a cache to check for duplicates
            String key = bean.getName() + ":" + bean.getType();
            boolean duplicate = beanCache.contains(key);
            if (!duplicate) {
                answer.addBean(bean);
                beanCache.add(key);
            }
        }

        return answer;
    }

    public void clearCache() {
        beanCache.clear();
    }

    protected void registerBean(
            CamelContext camelContext,
            List<BeanFactoryDefinition<?>> delayedRegistrations,
            BeanFactoryDefinition<?> def, boolean delayIfFailed) {

        CamelBeanPostProcessor cbpp = PluginHelper.getBeanPostProcessor(camelContext);
        Predicate<?> lazy = cbpp.getLazyBeanStrategy();

        String name = def.getName();
        String type = def.getType();
        try {
            // only do lazy bean on 2nd pass as 1st pass may work
            if (!delayIfFailed && lazy != null && lazy.test(null)) {
                bindLazyBean(camelContext, def, name, () -> {
                    try {
                        return BeanModelHelper.newInstance(def, camelContext);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                Object target = BeanModelHelper.newInstance(def, camelContext);
                bindBean(camelContext, def, name, target);
            }
        } catch (Exception e) {
            if (delayIfFailed) {
                delayedRegistrations.add(def);
            } else {
                String msg
                        = name != null ? "Error creating bean: " + name + " of type: " + type : "Error creating bean: " + type;
                throw new RuntimeException(msg + classNotFoundHint(camelContext, e), e);
            }
        }
    }

    private class BeansCustomizer implements CamelContextCustomizer {

        private final List<BeanFactoryDefinition<?>> delayedRegistrations = new ArrayList<>();
        private final List<BeanFactoryDefinition<?>> beans = new ArrayList<>();

        public void addBean(BeanFactoryDefinition<?> bean) {
            beans.add(bean);
        }

        @Override
        public void configure(CamelContext camelContext) {
            // first-pass of creating beans
            for (BeanFactoryDefinition<?> bean : beans) {
                registerBean(camelContext, delayedRegistrations, bean, true);
            }
            beans.clear();
            // second-pass of creating beans should fail if not possible
            for (BeanFactoryDefinition<?> bean : delayedRegistrations) {
                registerBean(camelContext, delayedRegistrations, bean, false);
            }
            delayedRegistrations.clear();
        }
    }

    protected void bindBean(
            CamelContext camelContext, BeanFactoryDefinition<?> def,
            String name, Object target)
            throws Exception {

        // unbind in case we reload
        camelContext.getRegistry().unbind(name);
        camelContext.getRegistry().bind(name, target, def.getInitMethod(), def.getDestroyMethod());

        // register bean in model
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        model.addCustomBean(def);
    }

    protected void bindLazyBean(
            CamelContext camelContext, BeanFactoryDefinition<?> def,
            String name, Supplier<Object> target)
            throws Exception {

        Class<?> beanType = null;
        if (def.getType() != null) {
            beanType = camelContext.getClassResolver().resolveClass(def.getType());
        }
        if (beanType == null) {
            beanType = Object.class;
        }

        // unbind in case we reload
        camelContext.getRegistry().unbind(name);
        camelContext.getRegistry().bind(name, beanType, target, def.getInitMethod(), def.getDestroyMethod());

        // register bean in model
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        model.addCustomBean(def);
    }

    /**
     * The cause of a bean that could not be created is a ClassNotFoundException more often than not (a wrong package, a
     * missing dependency); say so, and for a built-in Camel bean written with the wrong package name (or with no
     * package) the right one, from the bean metadata on the classpath.
     */
    static String classNotFoundHint(CamelContext camelContext, Throwable e) {
        String cls = PojoBeanHelper.missingClassName(e);
        if (cls == null) {
            return "";
        }
        String hint = ": class " + cls + " was not found";
        PojoBeanHelper.PojoBean bean = PojoBeanHelper.findByName(camelContext, cls);
        if (bean != null && !bean.javaType().equals(cls)) {
            return hint + " (did you mean " + bean.javaType()
                   + (bean.interfaceType() != null ? " (" + bean.interfaceType() + ")" : "")
                   + "? write: type: " + bean.javaType() + ")";
        }
        return hint + PojoBeanHelper.classNotFoundHint(camelContext, cls, null);
    }

}
