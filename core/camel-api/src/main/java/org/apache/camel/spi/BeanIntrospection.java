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
package org.apache.camel.spi;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StaticService;
import org.apache.camel.TypeConverter;

/**
 * Used for introspecting beans properties via Java reflection; such as extracting current property values, or updating
 * one or more properties etc.
 *
 * End users should favour using {@link org.apache.camel.support.PropertyBindingSupport} instead.
 */
public interface BeanIntrospection extends StaticService, AfterPropertiesConfigured {

    /**
     * Structure of an introspected class.
     */
    final class ClassInfo {
        public Class<?> clazz;
        public MethodInfo[] methods;
    }

    /**
     * Structure of an introspected method.
     */
    final class MethodInfo {
        public Method method;
        public Boolean isGetter;
        public Boolean isSetter;
        public String getterOrSetterShorthandName;
        public Boolean hasGetterAndSetter;
    }

    // Statistics
    // ----------------------------------------------------

    /**
     * Number of times bean introspection has been invoked
     */
    long getInvokedCounter();

    /**
     * Reset the statistics counters.
     */
    void resetCounters();

    /**
     * Whether to gather extended statistics for introspection usage.
     */
    boolean isExtendedStatistics();

    /**
     * Whether to gather extended statistics for introspection usage.
     */
    void setExtendedStatistics(boolean extendedStatistics);

    /**
     * Logging level used for logging introspection usage. Is using TRACE level as default.
     */
    LoggingLevel getLoggingLevel();

    /**
     * Logging level used for logging introspection usage. Is using TRACE level as default.
     */
    void setLoggingLevel(LoggingLevel loggingLevel);

    // Introspection
    // ----------------------------------------------------

    /**
     * Will inspect the target for properties.
     * <p/>
     * Notice a property must have both a getter/setter method to be included. Notice all <tt>null</tt> values will be
     * included.
     *
     * @param  target       the target bean
     * @param  properties   the map to fill in found properties
     * @param  optionPrefix an optional prefix to append the property key
     * @return              <tt>true</tt> if any properties was found, <tt>false</tt> otherwise.
     */
    boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix);

    /**
     * Will inspect the target for properties.
     * <p/>
     * Notice a property must have both a getter/setter method to be included.
     *
     * @param  target       the target bean
     * @param  properties   the map to fill in found properties
     * @param  optionPrefix an optional prefix to append the property key
     * @param  includeNull  whether to include <tt>null</tt> values
     * @return              <tt>true</tt> if any properties was found, <tt>false</tt> otherwise.
     */
    boolean getProperties(Object target, Map<String, Object> properties, String optionPrefix, boolean includeNull);

    /**
     * Introspects the given class.
     *
     * @param  clazz the class
     * @return       the introspection result as a {@link ClassInfo} structure.
     */
    ClassInfo cacheClass(Class<?> clazz);

    /**
     * Clears the introspection cache.
     */
    void clearCache();

    /**
     * Number of classes in the introspection cache.
     */
    long getCachedClassesCounter();

    /**
     * Gets the property or else returning the default value.
     *
     * @param  target       the target bean
     * @param  propertyName the property name
     * @param  defaultValue the default value
     * @param  ignoreCase   whether to ignore case for matching the property name
     * @return              the property value, or the default value if the target does not have a property with the
     *                      given name
     */
    Object getOrElseProperty(Object target, String propertyName, Object defaultValue, boolean ignoreCase);

    /**
     * Gets the getter method for the property.
     *
     * @param  type                  the target class
     * @param  propertyName          the property name
     * @param  ignoreCase            whether to ignore case for matching the property name
     * @return                       the getter method
     * @throws NoSuchMethodException is thrown if there are no getter method for the property
     */
    Method getPropertyGetter(Class<?> type, String propertyName, boolean ignoreCase) throws NoSuchMethodException;

    /**
     * Gets the setter method for the property.
     *
     * @param  type                  the target class
     * @param  propertyName          the property name
     * @return                       the setter method
     * @throws NoSuchMethodException is thrown if there are no setter method for the property
     */
    Method getPropertySetter(Class<?> type, String propertyName) throws NoSuchMethodException;

    /**
     * This method supports three modes to set a property:
     *
     * 1. Setting a Map property where the property name refers to a map via name[aKey] where aKey is the map key to
     * use.
     *
     * 2. Setting a property that has already been resolved, this is the case when {@code context} and {@code refName}
     * are NULL and {@code value} is non-NULL.
     *
     * 3. Setting a property that has not yet been resolved, the property will be resolved based on the suitable methods
     * found matching the property name on the {@code target} bean. For this mode to be triggered the parameters
     * {@code context} and {@code refName} must NOT be NULL, and {@code value} MUST be NULL.
     */
    boolean setProperty(CamelContext context, Object target, String name, Object value) throws Exception;

    /**
     * This method supports three modes to set a property:
     *
     * 1. Setting a Map property where the property name refers to a map via name[aKey] where aKey is the map key to
     * use.
     *
     * 2. Setting a property that has already been resolved, this is the case when {@code context} and {@code refName}
     * are NULL and {@code value} is non-NULL.
     *
     * 3. Setting a property that has not yet been resolved, the property will be resolved based on the suitable methods
     * found matching the property name on the {@code target} bean. For this mode to be triggered the parameters
     * {@code context} and {@code refName} must NOT be NULL, and {@code value} MUST be NULL.
     */
    boolean setProperty(
            CamelContext context, TypeConverter typeConverter, Object target, String name, Object value, String refName,
            boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase)
            throws Exception;

    /**
     * Find all the setter methods on the class
     */
    Set<Method> findSetterMethods(
            Class<?> clazz, String name, boolean allowBuilderPattern, boolean allowPrivateSetter, boolean ignoreCase);

}
