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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for dealing with configurers.
 *
 * @see org.apache.camel.spi.PropertyConfigurer
 * @see org.apache.camel.spi.PropertyConfigurerGetter
 */
public final class PropertyConfigurerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyConfigurerHelper.class);

    private PropertyConfigurerHelper() {
    }

    /**
     * Resolves the given configurer.
     *
     * @param  context the camel context
     * @param  target  the target object for which we need a {@link org.apache.camel.spi.PropertyConfigurer}
     * @return         the resolved configurer, or <code>null</code> if no configurer could be found
     */
    public static PropertyConfigurer resolvePropertyConfigurer(CamelContext context, Object target) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(context, "context");

        PropertyConfigurer configurer = null;

        if (target instanceof Component component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = component.getComponentPropertyConfigurer();
        }

        if (configurer == null) {
            configurer = resolvePropertyConfigurer(context, target.getClass());
        }
        return configurer;
    }

    /**
     * Resolves the given configurer.
     *
     * @param  context    the camel context
     * @param  targetType the target object type for which we need a {@link org.apache.camel.spi.PropertyConfigurer}
     * @return            the resolved configurer, or <code>null</code> if no configurer could be found
     */
    public static PropertyConfigurer resolvePropertyConfigurer(CamelContext context, Class<?> targetType) {
        ObjectHelper.notNull(targetType, "targetType");
        ObjectHelper.notNull(context, "context");

        // map FQN to configurer syntax for components/dataformats/languages
        if (targetType.getName().endsWith("Component")) {
            String name = StringHelper.before(targetType.getSimpleName(), "Component");
            name = StringHelper.camelCaseToDash(name) + "-component";
            return resolvePropertyConfigurerByNameForType(context, name, targetType);
        } else if (targetType.getName().endsWith("Language")) {
            String name = StringHelper.before(targetType.getSimpleName(), "Language");
            name = StringHelper.camelCaseToDash(name) + "-language";
            return resolvePropertyConfigurerByNameForType(context, name, targetType);
        } else if (targetType.getName().endsWith("DataFormat")) {
            String name = StringHelper.before(targetType.getSimpleName(), "DataFormat");
            name = StringHelper.camelCaseToDash(name) + "-dataformat";
            return resolvePropertyConfigurerByNameForType(context, name, targetType);
        }

        if (targetType.isAnonymousClass()) {
            return null;
        }

        // lookup configurer if there is any
        // use FQN class name first, then simple name, and root key last
        String[] names = new String[] {
                targetType.getName(), targetType.getSimpleName(),
                targetType.getName() + "-configurer", targetType.getSimpleName() + "-configurer" };
        for (String n : names) {
            PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(context).resolvePropertyConfigurer(n, context);
            if (configurer != null) {
                return configurer;
            }
        }
        return null;
    }

    /**
     * Resolves the configurer of a component, data format or language by its name (such as jackson-dataformat), and
     * makes sure it is the configurer of the given target.
     * <p>
     * Two JARs can register a configurer with the same name, and then the one first on the classpath is found. For
     * example both camel-jackson (Jackson 2.x) and camel-jackson3 (Jackson 3.x) register jackson-dataformat, while the
     * jackson data format is created from camel-jackson3 when both are on the classpath. When the configurer found by
     * name is generated for another type than the target, the configurer generated for the type of the target is used
     * instead.
     *
     * @param  context the camel context
     * @param  name    the name of the configurer, such as jackson-dataformat
     * @param  target  the component, data format or language to configure, or <code>null</code> to only resolve the
     *                 configurer by its name
     * @return         the resolved configurer, or <code>null</code> if no configurer could be found
     */
    public static PropertyConfigurer resolvePropertyConfigurerByName(CamelContext context, String name, Object target) {
        return resolvePropertyConfigurerByNameForType(context, name, target != null ? target.getClass() : null);
    }

    private static PropertyConfigurer resolvePropertyConfigurerByNameForType(
            CamelContext context, String name, Class<?> targetType) {
        ObjectHelper.notNull(context, "context");

        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(context).resolvePropertyConfigurer(name, context);
        if (targetType != null && configurer instanceof GeneratedPropertyConfigurer
                && !isGeneratedFor(configurer, targetType)) {
            PropertyConfigurer own = resolveGeneratedPropertyConfigurer(context, targetType);
            if (own != null) {
                LOG.debug("The configurer: {} found by name: {} is not for type: {}. Using the configurer: {} instead.",
                        configurer.getClass().getName(), name, targetType.getName(), own.getClass().getName());
                return own;
            }
        }
        return configurer;
    }

    /**
     * Whether the configurer is the one Camel generates for the type (or a super type), which is named after the type
     * with the Configurer suffix.
     */
    private static boolean isGeneratedFor(PropertyConfigurer configurer, Class<?> type) {
        String name = configurer.getClass().getName();
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            if (name.equals(t.getName() + "Configurer")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The configurer Camel generates for the type (or a super type), which is named after the type with the Configurer
     * suffix.
     */
    private static PropertyConfigurer resolveGeneratedPropertyConfigurer(CamelContext context, Class<?> type) {
        for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
            ClassLoader loader = t.getClassLoader();
            if (loader == null) {
                continue;
            }
            Class<?> clazz = context.getClassResolver().resolveClass(t.getName() + "Configurer", loader);
            if (clazz != null && GeneratedPropertyConfigurer.class.isAssignableFrom(clazz)) {
                return (PropertyConfigurer) context.getInjector().newInstance(clazz, false);
            }
        }
        return null;
    }

    /**
     * Resolves the given configurer.
     *
     * @param  context the camel context
     * @param  target  the target object for which we need a {@link org.apache.camel.spi.PropertyConfigurer}
     * @param  type    the specific type of {@link org.apache.camel.spi.PropertyConfigurer}
     * @return         the resolved configurer, or <code>null</code> if no configurer could be found
     */
    public static <T> T resolvePropertyConfigurer(CamelContext context, Object target, Class<T> type) {
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(context, "context");

        PropertyConfigurer configurer = resolvePropertyConfigurer(context, target);
        if (type.isInstance(configurer)) {
            return type.cast(configurer);
        }

        return null;
    }
}
