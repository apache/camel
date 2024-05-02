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
package org.apache.camel.model;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.util.StringHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Helper to create bean instances from bean model definitions.
 * <p/>
 * Creating beans is complex as Camel support many options such as constructor, factory beans, builder beans, scripts,
 * and much more. This helper hides this complexity for creating bean instances.
 */
public final class BeanModelHelper {

    private BeanModelHelper() {
    }

    public static Object newInstance(RegistryBeanDefinition def, CamelContext context) throws Exception {
        Object target;

        String type = def.getType();
        if (!type.startsWith("#")) {
            type = "#class:" + type;
        }

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

}
