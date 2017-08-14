/**
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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.LRUCacheFactory;

/**
 * Represents a cache of {@link MethodInfo} objects to avoid the expense of introspection for each
 * invocation of a method via a proxy.
 *
 * @version 
 */
public class MethodInfoCache {
    private final CamelContext camelContext;
    private Map<Method, MethodInfo> methodCache;
    private Map<Class<?>, BeanInfo> classCache;

    public MethodInfoCache(CamelContext camelContext) {
        this(camelContext, 1000, 10000);
    }

    public MethodInfoCache(CamelContext camelContext, int classCacheSize, int methodCacheSize) {
        this(camelContext, createClassCache(classCacheSize), createMethodCache(methodCacheSize));
    }

    public MethodInfoCache(CamelContext camelContext, Map<Class<?>, BeanInfo> classCache, Map<Method, MethodInfo> methodCache) {
        this.camelContext = camelContext;
        this.classCache = classCache;
        this.methodCache = methodCache;
    }

    public synchronized MethodInfo getMethodInfo(Method method) {
        MethodInfo answer = methodCache.get(method);
        if (answer == null) {
            answer = createMethodInfo(method);
            methodCache.put(method, answer);
        }
        return answer;
    }

    protected MethodInfo createMethodInfo(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        BeanInfo info = getBeanInfo(declaringClass);
        return info.getMethodInfo(method);
    }

    protected synchronized BeanInfo getBeanInfo(Class<?> declaringClass) {
        BeanInfo beanInfo = classCache.get(declaringClass);
        if (beanInfo == null) {
            beanInfo = createBeanInfo(declaringClass);
            classCache.put(declaringClass, beanInfo);
        }
        return beanInfo;
    }

    protected BeanInfo createBeanInfo(Class<?> declaringClass) {
        return new BeanInfo(camelContext, declaringClass);
    }

    @SuppressWarnings("unchecked")
    protected static <K, V> Map<K, V> createLruCache(int size) {
        // use a soft cache
        return LRUCacheFactory.newLRUSoftCache(size);
    }

    private static Map<Class<?>, BeanInfo> createClassCache(int size) {
        return createLruCache(size);
    }

    private static Map<Method, MethodInfo> createMethodCache(int size) {
        return createLruCache(size);
    }
}
