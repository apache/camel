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

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.Suppliers;

/**
 * Helper to create bean instances from bean model definitions.
 * <p/>
 * Creating beans is complex as Camel support many options such as constructor, factory beans, builder beans, scripts,
 * and much more. This helper hides this complexity for creating bean instances.
 */
public final class BeanModelHelper {

    private BeanModelHelper() {}

    /**
     * Creates a new bean.
     *
     * @param  def       the bean model
     * @param  context   the camel context
     * @return           the created bean instance
     * @throws Exception is thrown if error creating the bean
     */
    public static Object newInstance(BeanFactoryDefinition def, CamelContext context) throws Exception {
        Object target;

        String type = def.getType();
        if (!type.startsWith("#")) {
            type = "#class:" + type;
        }

        // script bean
        if (def.getScriptLanguage() != null && def.getScript() != null) {
            String script = resolveScript(context, def);
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
                target = slan.evaluate(script, bindings, clazz);
            } else {
                Exchange dummy = ExchangeHelper.getDummy(context);
                String text = ScriptHelper.resolveOptionalExternalScript(context, dummy, script);
                Expression exp = lan.createExpression(text);
                target = exp.evaluate(dummy, clazz);
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

        // do not set properties when using #type as it uses an existing shared bean
        boolean setProps = !type.startsWith("#type");
        if (setProps) {
            // set optional properties on created bean
            if (def.getProperties() != null && !def.getProperties().isEmpty()) {
                PropertyBindingSupport.setPropertiesOnTarget(context, target, def.getProperties());
            }
        }

        return target;
    }

    /**
     * Creates and binds the bean to the route-template repository (local beans for kamelets).
     *
     * @param  def                  the bean factory to bind.
     * @param  routeTemplateContext the context into which the bean factory should be bound.
     * @throws Exception            if an error occurs while trying to bind the bean factory
     */
    public static void bind(BeanFactoryDefinition<?> def, RouteTemplateContext routeTemplateContext) throws Exception {

        final Map<String, Object> props = new HashMap<>();
        if (def.getProperties() != null) {
            props.putAll(def.getProperties());
        }
        if (def.getBeanSupplier() != null) {
            if (props.isEmpty()) {
                // bean class is optional for supplier
                if (def.getBeanClass() != null) {
                    routeTemplateContext.bind(def.getName(), def.getBeanClass(), def.getBeanSupplier());
                } else {
                    routeTemplateContext.bind(def.getName(), def.getBeanSupplier());
                }
            }
        } else if (def.getScript() != null && def.getScriptLanguage() != null) {
            final CamelContext camelContext = routeTemplateContext.getCamelContext();
            final Language lan = camelContext.resolveLanguage(def.getScriptLanguage());
            final Class<?> clazz;
            if (def.getBeanClass() != null) {
                clazz = def.getBeanClass();
            } else if (def.getType() != null) {
                String fqn = def.getType();
                if (fqn.contains(":")) {
                    fqn = StringHelper.after(fqn, ":");
                }
                clazz = camelContext.getClassResolver().resolveMandatoryClass(fqn);
            } else {
                clazz = Object.class;
            }
            final String script = resolveScript(camelContext, def);
            final ScriptingLanguage slan = lan instanceof ScriptingLanguage ? (ScriptingLanguage) lan : null;
            if (slan != null) {
                // scripting language should be evaluated with route template context as binding
                // and memorize so the script is only evaluated once and the local bean is the same
                // if a route template refers to the local bean multiple times
                routeTemplateContext.bind(def.getName(), clazz, Suppliers.memorize(() -> {
                    Object local;
                    Map<String, Object> bindings = new HashMap<>();
                    // use rtx as the short-hand name, as context would imply its CamelContext
                    bindings.put("rtc", routeTemplateContext);
                    try {
                        local = slan.evaluate(script, bindings, Object.class);
                        if (!props.isEmpty()) {
                            PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                        }
                        if (def.getInitMethod() != null) {
                            org.apache.camel.support.ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                        }
                        if (def.getDestroyMethod() != null) {
                            routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot create bean: " + def.getType(), e);
                    }
                    return local;
                }));
            } else {
                // exchange based languages needs a dummy exchange to be evaluated
                // and memorize so the script is only evaluated once and the local bean is the same
                // if a route template refers to the local bean multiple times
                routeTemplateContext.bind(def.getName(), clazz, Suppliers.memorize(() -> {
                    try {
                        Exchange dummy = ExchangeHelper.getDummy(camelContext);
                        String text = ScriptHelper.resolveOptionalExternalScript(camelContext, dummy, script);
                        if (text != null) {
                            Expression exp = lan.createExpression(text);
                            Object local = exp.evaluate(dummy, clazz);
                            if (!props.isEmpty()) {
                                PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                            }
                            if (def.getInitMethod() != null) {
                                try {
                                    org.apache.camel.support.ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                                } catch (Exception e) {
                                    throw RuntimeCamelException.wrapRuntimeException(e);
                                }
                            }
                            if (def.getDestroyMethod() != null) {
                                routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                            }
                            return local;
                        } else {
                            return null;
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot create bean: " + def.getType(), e);
                    }
                }));
            }
        } else if (def.getBeanClass() != null || def.getType() != null) {
            String type = def.getType();
            if (type == null) {
                type = def.getBeanClass().getName();
            }
            if (!type.startsWith("#")) {
                type = "#class:" + type;
            }
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
            final String classType = type;

            final CamelContext camelContext = routeTemplateContext.getCamelContext();
            routeTemplateContext.bind(def.getName(), Object.class, Suppliers.memorize(() -> {
                try {
                    Object local = PropertyBindingSupport.resolveBean(camelContext, classType);

                    // do not set properties when using #type as it uses an existing shared bean
                    boolean setProps = !classType.startsWith("#type");
                    if (setProps) {
                        // set optional properties on created bean
                        if (def.getProperties() != null && !def.getProperties().isEmpty()) {
                            PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, def.getProperties());
                        }
                    }
                    if (def.getInitMethod() != null) {
                        org.apache.camel.support.ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                    }
                    if (def.getDestroyMethod() != null) {
                        routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                    }
                    return local;
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create bean: " + def.getType(), e);
                }
            }));
        } else {
            // invalid syntax for the local bean, so lets report an exception
            throw new IllegalArgumentException(
                    "Route template local bean: " + def.getName() + " has invalid type syntax: " + def.getType()
                            + ". To refer to a class then prefix the value with #class such as: #class:fullyQualifiedClassName");
        }
    }

    private static String resolveScript(CamelContext camelContext, BeanFactoryDefinition<?> def) {
        String answer = def.getScript();
        if (answer != null && !"false".equals(def.getScriptPropertyPlaceholders())) {
            answer = camelContext.resolvePropertyPlaceholders(answer);
        }
        return answer;
    }
}
