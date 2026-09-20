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
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ObjectHelper;
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

    private BeanModelHelper() {
    }

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

        boolean script = def.getScriptLanguage() != null && def.getScript() != null;
        boolean viaBuilder = def.getBuilderClass() != null;
        boolean viaInferredBuilder = false;

        // the type (class name) is optional for a bean created by a script or a builder
        String type = def.getType();
        if (type == null && !script && !viaBuilder) {
            throw new IllegalArgumentException(
                    "Bean " + def.getName() + " must have a type (class name) unless created by a script or a builder");
        }
        if (type != null && !type.startsWith("#")) {
            type = "#class:" + type;
        }

        // script bean
        if (script) {
            String text = resolveScript(context, def);
            // create bean via the script
            final Language lan = context.resolveLanguage(def.getScriptLanguage());
            final ScriptingLanguage slan = lan instanceof ScriptingLanguage sl ? sl : null;
            final Class<?> clazz;
            if (def.getType() != null) {
                String fqn = def.getType();
                if (fqn.startsWith("#class:")) {
                    fqn = fqn.substring(7);
                }
                clazz = context.getClassResolver().resolveMandatoryClass(fqn);
            } else {
                clazz = Object.class;
            }
            if (slan != null) {
                // scripting language should be evaluated with context as binding
                Map<String, Object> bindings = new HashMap<>();
                bindings.put("context", context);
                target = slan.evaluate(text, bindings, clazz);
            } else {
                Exchange dummy = ExchangeHelper.getDummy(context);
                String external = ScriptHelper.resolveOptionalExternalScript(context, dummy, text);
                Expression exp = lan.createExpression(external);
                target = exp.evaluate(dummy, clazz);
            }

            // a bean must be created
            if (target == null) {
                throw new NoSuchBeanException(def.getName(), "Creating bean using script returned null");
            }
        } else if (viaBuilder) {
            // builder class and method
            Class<?> clazz = context.getClassResolver().resolveMandatoryClass(def.getBuilderClass());
            Object builder = context.getInjector().newInstance(clazz);
            String bm = def.getBuilderMethod() != null ? def.getBuilderMethod() : PropertyBindingSupport.DEFAULT_BUILDER_METHOD;
            target = newInstanceViaBuilder(context, builder, bm, def.getProperties());
        } else {
            Object builder = inferBuilder(context, def, type);
            if (builder != null) {
                // the type has no public no-arg constructor but a builder, so create the bean via the builder
                String bm = PropertyBindingSupport.findBuilderMethod(builder, resolveBeanClass(context, type),
                        def.getBuilderMethod());
                target = newInstanceViaBuilder(context, builder, bm, def.getProperties());
                setRemainingProperties(context, target, def.getProperties(), builder);
                viaInferredBuilder = true;
            } else {
                target = PropertyBindingSupport.resolveBean(context, factoryOrConstructorType(def, type));
            }
        }

        // do not set properties when using #type as it uses an existing shared bean
        boolean setProps = !viaInferredBuilder && (type == null || !type.startsWith("#type"));
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
    public static void bind(BeanFactoryDefinition<?> def, RouteTemplateContext routeTemplateContext)
            throws Exception {

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
            final ScriptingLanguage slan = lan instanceof ScriptingLanguage sl ? sl : null;
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
                            ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                        }
                        if (def.getDestroyMethod() != null) {
                            routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "Cannot create bean: " + def.getType(), e);
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
                                    ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
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
                        throw new IllegalStateException(
                                "Cannot create bean: " + def.getType(), e);
                    }
                }));
            }
        } else if (def.getBuilderClass() != null) {
            // builder class and method, the type (class name) is optional
            final CamelContext camelContext = routeTemplateContext.getCamelContext();
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
            // memorize so the bean is only created once and the local bean is the same
            // if a route template refers to the local bean multiple times
            routeTemplateContext.bind(def.getName(), clazz, Suppliers.memorize(() -> {
                try {
                    Class<?> builderClass = camelContext.getClassResolver().resolveMandatoryClass(def.getBuilderClass());
                    Object builder = camelContext.getInjector().newInstance(builderClass);
                    String bm = def.getBuilderMethod() != null
                            ? def.getBuilderMethod() : PropertyBindingSupport.DEFAULT_BUILDER_METHOD;
                    Object local = newInstanceViaBuilder(camelContext, builder, bm, props);
                    // set the optional properties the builder did not take on the created bean
                    if (!props.isEmpty()) {
                        PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                    }
                    if (def.getInitMethod() != null) {
                        ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                    }
                    if (def.getDestroyMethod() != null) {
                        routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                    }
                    return local;
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Cannot create bean: " + def.getName() + " using builder: " + def.getBuilderClass(), e);
                }
            }));
        } else if (def.getBeanClass() != null || def.getType() != null) {
            String type = def.getType();
            if (type == null) {
                type = def.getBeanClass().getName();
            }
            if (!type.startsWith("#")) {
                type = "#class:" + type;
            }
            final String beanType = type;
            final String classType = factoryOrConstructorType(def, type);

            final CamelContext camelContext = routeTemplateContext.getCamelContext();
            routeTemplateContext.bind(def.getName(), Object.class, Suppliers.memorize(() -> {
                try {
                    Object local;
                    Object builder = inferBuilder(camelContext, def, beanType);
                    if (builder != null) {
                        // the type has no public no-arg constructor but a builder, so create the bean via the builder
                        String bm = PropertyBindingSupport.findBuilderMethod(builder, resolveBeanClass(camelContext, beanType),
                                def.getBuilderMethod());
                        local = newInstanceViaBuilder(camelContext, builder, bm, props);
                        setRemainingProperties(camelContext, local, props, builder);
                    } else {
                        local = PropertyBindingSupport.resolveBean(camelContext, classType);
                    }

                    // do not set properties when using #type as it uses an existing shared bean
                    boolean setProps = builder == null && !classType.startsWith("#type");
                    if (setProps) {
                        // set optional properties on created bean (the properties the builder took are removed)
                        if (!props.isEmpty()) {
                            PropertyBindingSupport.setPropertiesOnTarget(camelContext, local, props);
                        }
                    }
                    if (def.getInitMethod() != null) {
                        ObjectHelper.invokeMethodSafe(def.getInitMethod(), local);
                    }
                    if (def.getDestroyMethod() != null) {
                        routeTemplateContext.registerDestroyMethod(def.getName(), def.getDestroyMethod());
                    }
                    return local;
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Cannot create bean: " + def.getType(), e);
                }
            }));
        } else {
            // invalid syntax for the local bean, so lets report an exception
            throw new IllegalArgumentException(
                    "Route template local bean: " + def.getName() + " has invalid type syntax: " + def.getType()
                                               + ". To refer to a class then prefix the value with #class such as: #class:fullyQualifiedClassName");
        }
    }

    /**
     * Appends the factory method and constructor arguments of the bean definition to the <tt>#class:</tt> type, in the
     * syntax {@link PropertyBindingSupport#resolveBean(CamelContext, Object)} understands.
     */
    private static String factoryOrConstructorType(BeanFactoryDefinition<?> def, String type) {
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
        return type;
    }

    /**
     * Infers the builder of a bean whose type has no public no-arg constructor but a <tt>builder()</tt> (or
     * <tt>newBuilder()</tt>) method, so a class such as a LangChain4j model or a Lombok <tt>@Builder</tt> class can be
     * declared with only its type and properties. Nothing is inferred when the definition says how to create the bean
     * (a builder class, a factory method, or constructor arguments), or when the type is not a <tt>#class:</tt>.
     *
     * @return the builder to set the properties on, or <tt>null</tt> if the bean is not created via an inferred builder
     */
    private static Object inferBuilder(CamelContext camelContext, BeanFactoryDefinition<?> def, String type) throws Exception {
        boolean explicit = def.getBuilderClass() != null || def.getFactoryMethod() != null || def.getFactoryBean() != null
                || def.getConstructors() != null && !def.getConstructors().isEmpty();
        if (explicit || type == null || !type.startsWith("#class:")) {
            return null;
        }
        // the type can also carry a factory method or constructor arguments (#class:com.foo.Bar#create('x'))
        if (type.indexOf('#', 7) != -1 || type.indexOf('(') != -1) {
            return null;
        }
        return PropertyBindingSupport.newBuilderInstance(resolveBeanClass(camelContext, type));
    }

    private static Class<?> resolveBeanClass(CamelContext camelContext, String type) throws ClassNotFoundException {
        String fqn = type.startsWith("#class:") ? type.substring(7) : type;
        return camelContext.getClassResolver().resolveMandatoryClass(fqn);
    }

    /**
     * Creates the bean by setting the properties on the builder (the properties the builder accepts are removed from
     * the map, so the remaining can be set on the created bean) and invoking the builder method.
     */
    private static Object newInstanceViaBuilder(
            CamelContext camelContext, Object builder, String builderMethod, Map<String, Object> properties) {
        return PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withTarget(builder)
                .withRemoveParameters(true)
                .withProperties(properties)
                .build(Object.class, builderMethod);
    }

    /**
     * Sets the properties the builder did not take on the created bean, and names the properties the builder and the
     * bean accept when one of them is unknown, as the property names of a builder are not those of the bean.
     */
    private static void setRemainingProperties(
            CamelContext camelContext, Object target, Map<String, Object> properties, Object builder) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        try {
            PropertyBindingSupport.setPropertiesOnTarget(camelContext, target, properties);
        } catch (PropertyBindingException e) {
            throw new IllegalArgumentException(e.getMessage() + ". " + builderPropertiesHint(builder, target.getClass()), e);
        }
    }

    /**
     * Names the properties a bean created via its builder accepts: those of the builder, and those of the bean.
     */
    public static String builderPropertiesHint(Object builder, Class<?> type) {
        StringBuilder sb = new StringBuilder();
        sb.append("The bean is created through its builder ").append(builder.getClass().getName())
                .append(", which accepts: ")
                .append(String.join(", ", PropertyBindingSupport.builderPropertyNames(builder.getClass())));
        List<String> setters = PropertyBindingSupport.setterPropertyNames(type);
        if (!setters.isEmpty()) {
            sb.append("; the created bean accepts: ").append(String.join(", ", setters));
        }
        return sb.toString();
    }

    private static String resolveScript(CamelContext camelContext, BeanFactoryDefinition<?> def) {
        String answer = def.getScript();
        if (answer != null && !"false".equals(def.getScriptPropertyPlaceholders())) {
            answer = camelContext.resolvePropertyPlaceholders(answer);
        }
        return answer;
    }
}
