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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerSupport;
import org.apache.camel.model.Model;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.YamlIn;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public static final Logger LOG = LoggerFactory.getLogger(BeansDeserializer.class);

    private final Set<String> beanCache = new HashSet<>();
    private final Map<String, KeyValueHolder<Object, String>> beansToDestroy = new LinkedHashMap<>();

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
            if (bean.getScriptLanguage() != null || bean.getScript() != null) {
                ObjectHelper.notNull(bean.getScriptLanguage(), "The bean script language must be set");
                ObjectHelper.notNull(bean.getScript(), "The bean script must be set");
            }

            // due to yaml-dsl is pre parsing beans which gets created eager
            // and then later beans can be parsed again such as from Camel K Integration CRD files
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

    public Object newInstance(RegistryBeanDefinition def, CamelContext context) throws Exception {
        Object target;

        String type = def.getType();

        // script bean
        if (def.getScriptLanguage() != null && def.getScript() != null) {
            // create bean via the script
            final Language lan = context.resolveLanguage(def.getScriptLanguage());
            final ScriptingLanguage slan = lan instanceof ScriptingLanguage ? (ScriptingLanguage) lan : null;
            String fqn = def.getType();
            if (fqn.startsWith("#class:")) {
                fqn = fqn.substring(7);
            }
            final Class<?> clazz = context.getClassResolver().resolveMandatoryClass(fqn);
            if (slan != null) {
                // scripting language should be evaluated with context as binding
                Map<String, Object> bindings = new HashMap<>();
                bindings.put("context", context);
                target = slan.evaluate(def.getScript(), bindings, clazz);
            } else {
                // exchange based languages needs a dummy exchange to be evaluated
                ExchangeFactory ef = context.getCamelContextExtension().getExchangeFactory();
                Exchange dummy = ef.create(false);
                try {
                    String text = ScriptHelper.resolveOptionalExternalScript(context, dummy, def.getScript());
                    Expression exp = lan.createExpression(text);
                    target = exp.evaluate(dummy, clazz);
                } finally {
                    ef.release(dummy);
                }
            }

            // a bean must be created
            if (target == null) {
                throw new NoSuchBeanException(def.getName(), "Creating bean using script returned null");
            }
        } else if (def.getBuilderClass() != null) {
            // builder class and method
            Class<?> clazz = context.getClassResolver().resolveMandatoryClass(def.getBuilderClass());
            Object builder = context.getInjector().newInstance(clazz);
            String bm = def.getBuilderMethod() != null ? def.getBuilderMethod() : "build";

            // create bean via builder and assign as target output
            target = PropertyBindingSupport.build()
                    .withCamelContext(context)
                    .withTarget(builder)
                    .withRemoveParameters(true)
                    .withProperties(def.getProperties())
                    .build(Object.class, bm);
        } else {
            // factory bean/method
            if (def.getFactoryBean() != null && def.getFactoryMethod() != null) {
                type = type + "#" + def.getFactoryBean() + ":" + def.getFactoryMethod();
            } else if (def.getFactoryMethod() != null) {
                type = type + "#" + def.getFactoryMethod();
            }
            // property binding support has constructor arguments as part of the type
            StringJoiner ctr = new StringJoiner(", ");
            if (def.getConstructors() != null && !def.getConstructors().isEmpty()) {
                // need to sort constructor args based on index position
                Map<Integer, Object> sorted = new TreeMap<>(def.getConstructors());
                for (Object val : sorted.values()) {
                    String text = val.toString();
                    if (!StringHelper.isQuoted(text)) {
                        text = "\"" + text + "\"";
                    }
                    ctr.add(text);
                }
                type = type + "(" + ctr + ")";
            }

            target = PropertyBindingSupport.resolveBean(context, type);
        }

        // set optional properties on created bean
        if (def.getProperties() != null && !def.getProperties().isEmpty()) {
            PropertyBindingSupport.setPropertiesOnTarget(context, target, def.getProperties());
        }

        return target;
    }

    protected void registerBean(
            CamelContext camelContext,
            List<RegistryBeanDefinition> delayedRegistrations,
            RegistryBeanDefinition def, boolean delayIfFailed) {

        String name = def.getName();
        String type = def.getType();
        try {
            Object target = newInstance(def, camelContext);
            bindBean(camelContext, def, name, target);
        } catch (Exception e) {
            if (delayIfFailed) {
                delayedRegistrations.add(def);
            } else {
                String msg
                        = name != null ? "Error creating bean: " + name + " of type: " + type : "Error creating bean: " + type;
                throw new RuntimeException(msg, e);
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

    protected void bindBean(
            CamelContext camelContext, RegistryBeanDefinition def,
            String name, Object target)
            throws Exception {

        // destroy and unbind any existing bean
        destroyBean(name, true);
        camelContext.getRegistry().unbind(name);

        // invoke init method and register bean
        String initMethod = def.getInitMethod();
        if (initMethod != null) {
            org.apache.camel.support.ObjectHelper.invokeMethodSafe(initMethod, target);
        }
        camelContext.getRegistry().bind(name, target);

        // remember to destroy bean on shutdown
        if (def.getDestroyMethod() != null) {
            beansToDestroy.put(name, new KeyValueHolder<>(target, def.getDestroyMethod()));
        }

        // register bean in model
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        model.addRegistryBean(def);
    }

    protected void destroyBean(String name, boolean remove) {
        var holder = remove ? beansToDestroy.remove(name) : beansToDestroy.get(name);
        if (holder != null) {
            String destroyMethod = holder.getValue();
            Object target = holder.getKey();
            try {
                org.apache.camel.support.ObjectHelper.invokeMethodSafe(destroyMethod, target);
            } catch (Exception e) {
                LOG.warn("Error invoking destroy method: {} on bean: {} due to: {}. This exception is ignored.",
                        destroyMethod, target, e.getMessage(), e);
            }
        }
    }

    public void stop() throws Exception {
        // beans should trigger destroy methods on shutdown
        for (String name : beansToDestroy.keySet()) {
            destroyBean(name, false);
        }
        beansToDestroy.clear();
    }

}
