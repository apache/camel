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
package org.apache.camel.support.management;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.management.Descriptor;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedNotification;
import org.apache.camel.api.management.ManagedNotifications;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel specific {@link javax.management.MBeanInfo} assembler that reads the details from the
 * {@link ManagedResource}, {@link ManagedAttribute}, {@link ManagedOperation}, {@link ManagedNotification}, and
 * {@link ManagedNotifications} annotations.
 */
public class MBeanInfoAssembler implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(MBeanInfoAssembler.class);

    private final BeanIntrospection beanIntrospection;

    // use a cache to speedup gathering JMX MBeanInfo for known classes
    // use a weak cache as we don't want the cache to keep around as it reference classes
    // which could prevent classloader to unload classes if being referenced from this cache
    private Map<Class<?>, MBeanAttributesAndOperations> cache;

    public MBeanInfoAssembler(CamelContext camelContext) {
        this.beanIntrospection = PluginHelper.getBeanIntrospection(camelContext);
    }

    @Override
    public void start() {
        cache = LRUCacheFactory.newLRUWeakCache(1000);
    }

    @Override
    public void stop() {
        if (cache != null) {
            if (LOG.isDebugEnabled() && cache instanceof LRUCache<Class<?>, MBeanAttributesAndOperations> lruCache) {
                LOG.debug("Clearing cache[size={}, hits={}, misses={}, evicted={}]", lruCache.size(), lruCache.getHits(),
                        lruCache.getMisses(), lruCache.getEvicted());
            }
            cache.clear();
        }
    }

    /**
     * Structure to hold cached mbean attributes and operations for a given class.
     */
    private static final class MBeanAttributesAndOperations {
        private Map<String, ManagedAttributeInfo> attributes;
        private Set<ManagedOperationInfo> operations;
    }

    /**
     * Gets the {@link ModelMBeanInfo} for the given managed bean
     *
     * @param  defaultManagedBean the default managed bean
     * @param  customManagedBean  an optional custom managed bean
     * @param  objectName         the object name
     * @return                    the model info, or <tt>null</tt> if not possible to create, for example due the
     *                            managed bean is a proxy class
     * @throws JMException        is thrown if error creating the model info
     */
    public ModelMBeanInfo getMBeanInfo(
            CamelContext camelContext, Object defaultManagedBean, Object customManagedBean, String objectName)
            throws JMException {

        // skip proxy classes
        if (defaultManagedBean != null && Proxy.isProxyClass(defaultManagedBean.getClass())) {
            LOG.trace("Skip creating ModelMBeanInfo due proxy class {}", defaultManagedBean.getClass());
            return null;
        }

        // maps and lists to contain information about attributes and operations
        Map<String, ManagedAttributeInfo> attributes = new LinkedHashMap<>();
        Set<ManagedOperationInfo> operations = new LinkedHashSet<>();
        Set<ModelMBeanAttributeInfo> mBeanAttributes = new LinkedHashSet<>();
        Set<ModelMBeanOperationInfo> mBeanOperations = new LinkedHashSet<>();
        Set<ModelMBeanNotificationInfo> mBeanNotifications = new LinkedHashSet<>();

        // extract details from default managed bean
        if (defaultManagedBean != null) {
            extractAttributesAndOperations(camelContext, defaultManagedBean.getClass(), attributes, operations);
            extractMbeanAttributes(attributes, mBeanAttributes, mBeanOperations);
            extractMbeanOperations(operations, mBeanOperations);
            extractMbeanNotifications(defaultManagedBean, mBeanNotifications);
        }

        // extract details from custom managed bean
        if (customManagedBean != null) {
            extractAttributesAndOperations(camelContext, customManagedBean.getClass(), attributes, operations);
            extractMbeanAttributes(attributes, mBeanAttributes, mBeanOperations);
            extractMbeanOperations(operations, mBeanOperations);
            extractMbeanNotifications(customManagedBean, mBeanNotifications);
        }

        // create the ModelMBeanInfo
        String name = getName(customManagedBean != null ? customManagedBean : defaultManagedBean);
        String description = getDescription(customManagedBean != null ? customManagedBean : defaultManagedBean, objectName);
        ModelMBeanAttributeInfo[] arrayAttributes
                = mBeanAttributes.toArray(new ModelMBeanAttributeInfo[0]);
        ModelMBeanOperationInfo[] arrayOperations
                = mBeanOperations.toArray(new ModelMBeanOperationInfo[0]);
        ModelMBeanNotificationInfo[] arrayNotifications
                = mBeanNotifications.toArray(new ModelMBeanNotificationInfo[0]);

        ModelMBeanInfo info
                = new ModelMBeanInfoSupport(name, description, arrayAttributes, null, arrayOperations, arrayNotifications);
        LOG.trace("Created ModelMBeanInfo {}", info);
        return info;
    }

    private void extractAttributesAndOperations(
            CamelContext camelContext, Class<?> managedClass, Map<String, ManagedAttributeInfo> attributes,
            Set<ManagedOperationInfo> operations) {
        MBeanAttributesAndOperations cached = cache.get(managedClass);
        if (cached == null) {
            doExtractAttributesAndOperations(managedClass, attributes, operations);
            cached = new MBeanAttributesAndOperations();
            cached.attributes = new LinkedHashMap<>(attributes);
            cached.operations = new LinkedHashSet<>(operations);

            // clear before we re-add them
            attributes.clear();
            operations.clear();

            // add to cache
            cache.put(managedClass, cached);
        }

        attributes.putAll(cached.attributes);
        operations.addAll(cached.operations);
    }

    private void doExtractAttributesAndOperations(
            Class<?> managedClass, Map<String, ManagedAttributeInfo> attributes,
            Set<ManagedOperationInfo> operations) {
        // extract the class
        doDoExtractAttributesAndOperations(managedClass, attributes, operations);

        // and then any sub classes
        if (managedClass.getSuperclass() != null) {
            Class<?> clazz = managedClass.getSuperclass();
            // skip any JDK classes
            if (!clazz.getName().startsWith("java")) {
                LOG.trace("Extracting attributes and operations from sub class: {}", clazz);
                doExtractAttributesAndOperations(clazz, attributes, operations);
            }
        }

        // and then any additional interfaces (as interfaces can be annotated as well)
        if (managedClass.getInterfaces() != null) {
            for (Class<?> clazz : managedClass.getInterfaces()) {
                // recursive as there may be multiple interfaces
                if (clazz.getName().startsWith("java")) {
                    // skip any JDK classes
                    continue;
                }
                LOG.trace("Extracting attributes and operations from implemented interface: {}", clazz);
                doExtractAttributesAndOperations(clazz, attributes, operations);
            }
        }
    }

    private void doDoExtractAttributesAndOperations(
            Class<?> managedClass, Map<String, ManagedAttributeInfo> attributes,
            Set<ManagedOperationInfo> operations) {
        LOG.trace("Extracting attributes and operations from class: {}", managedClass);

        // introspect the class, and leverage the cache to have better performance
        BeanIntrospection.ClassInfo cache = beanIntrospection.cacheClass(managedClass);

        for (BeanIntrospection.MethodInfo cacheInfo : cache.methods) {
            // must be from declaring class
            if (cacheInfo.method.getDeclaringClass() != managedClass) {
                continue;
            }

            LOG.trace("Extracting attributes and operations from method: {}", cacheInfo.method);
            ManagedAttribute ma = cacheInfo.method.getAnnotation(ManagedAttribute.class);
            if (ma != null) {
                String key;
                String desc = ma.description();
                Method getter = null;
                Method setter = null;
                boolean mask = ma.mask();

                if (cacheInfo.isGetter) {
                    key = cacheInfo.getterOrSetterShorthandName;
                    getter = cacheInfo.method;
                } else if (cacheInfo.isSetter) {
                    key = cacheInfo.getterOrSetterShorthandName;
                    setter = cacheInfo.method;
                } else {
                    throw new IllegalArgumentException(
                            "@ManagedAttribute can only be used on Java bean methods, was: " + cacheInfo.method + " on bean: "
                                                       + managedClass);
                }

                // they key must be capitalized
                key = StringHelper.capitalize(key);

                // lookup first
                ManagedAttributeInfo info = attributes.get(key);
                if (info == null) {
                    info = new ManagedAttributeInfo(key, desc);
                }
                if (getter != null) {
                    info.setGetter(getter);
                }
                if (setter != null) {
                    info.setSetter(setter);
                }
                info.setMask(mask);

                attributes.put(key, info);
            }

            // operations
            ManagedOperation mo = cacheInfo.method.getAnnotation(ManagedOperation.class);
            if (mo != null) {
                String desc = mo.description();
                Method operation = cacheInfo.method;
                boolean mask = mo.mask();
                operations.add(new ManagedOperationInfo(desc, operation, mask));
            }
        }
    }

    private void extractMbeanAttributes(
            Map<String, ManagedAttributeInfo> attributes,
            Set<ModelMBeanAttributeInfo> mBeanAttributes, Set<ModelMBeanOperationInfo> mBeanOperations)
            throws IntrospectionException {

        for (ManagedAttributeInfo info : attributes.values()) {
            ModelMBeanAttributeInfo mbeanAttribute
                    = new ModelMBeanAttributeInfo(info.getKey(), info.getDescription(), info.getGetter(), info.getSetter());

            // add missing attribute descriptors, this is needed to have attributes accessible
            Descriptor desc = mbeanAttribute.getDescriptor();

            desc.setField("mask", info.isMask() ? "true" : "false");
            if (info.getGetter() != null) {
                desc.setField("getMethod", info.getGetter().getName());
                // attribute must also be added as mbean operation
                ModelMBeanOperationInfo mbeanOperation = new ModelMBeanOperationInfo(info.getKey(), info.getGetter());
                Descriptor opDesc = mbeanOperation.getDescriptor();
                opDesc.setField("mask", info.isMask() ? "true" : "false");
                mbeanOperation.setDescriptor(opDesc);
                mBeanOperations.add(mbeanOperation);
            }
            if (info.getSetter() != null) {
                desc.setField("setMethod", info.getSetter().getName());
                // attribute must also be added as mbean operation
                ModelMBeanOperationInfo mbeanOperation = new ModelMBeanOperationInfo(info.getKey(), info.getSetter());
                mBeanOperations.add(mbeanOperation);
            }
            mbeanAttribute.setDescriptor(desc);

            mBeanAttributes.add(mbeanAttribute);
            LOG.trace("Assembled attribute: {}", mbeanAttribute);
        }
    }

    private void extractMbeanOperations(
            Set<ManagedOperationInfo> operations, Set<ModelMBeanOperationInfo> mBeanOperations) {
        for (ManagedOperationInfo info : operations) {
            ModelMBeanOperationInfo mbean = new ModelMBeanOperationInfo(info.description(), info.operation());
            Descriptor opDesc = mbean.getDescriptor();
            opDesc.setField("mask", info.mask() ? "true" : "false");
            mbean.setDescriptor(opDesc);
            mBeanOperations.add(mbean);
            LOG.trace("Assembled operation: {}", mbean);
        }
    }

    private void extractMbeanNotifications(Object managedBean, Set<ModelMBeanNotificationInfo> mBeanNotifications) {
        ManagedNotifications notifications = managedBean.getClass().getAnnotation(ManagedNotifications.class);
        if (notifications != null) {
            for (ManagedNotification notification : notifications.value()) {
                ModelMBeanNotificationInfo info = new ModelMBeanNotificationInfo(
                        notification.notificationTypes(), notification.name(), notification.description());
                mBeanNotifications.add(info);
                LOG.trace("Assembled notification: {}", info);
            }
        }
    }

    private String getDescription(Object managedBean, String objectName) {
        ManagedResource mr = ObjectHelper.getAnnotation(managedBean, ManagedResource.class);
        return mr != null ? mr.description() : "";
    }

    private String getName(Object managedBean) {
        return managedBean.getClass().getName();
    }

    private static final class ManagedAttributeInfo {
        private final String key;
        private final String description;
        private Method getter;
        private Method setter;
        private boolean mask;

        private ManagedAttributeInfo(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }

        public Method getGetter() {
            return getter;
        }

        public void setGetter(Method getter) {
            this.getter = getter;
        }

        public Method getSetter() {
            return setter;
        }

        public void setSetter(Method setter) {
            this.setter = setter;
        }

        public boolean isMask() {
            return mask;
        }

        public void setMask(boolean mask) {
            this.mask = mask;
        }

        @Override
        public String toString() {
            return "ManagedAttributeInfo: [" + key + " + getter: " + getter + ", setter: " + setter + "]";
        }
    }

    private record ManagedOperationInfo(String description, Method operation, boolean mask) {

        @Override
        public String toString() {
            return "ManagedOperationInfo: [" + operation + "]";
        }
    }

}
