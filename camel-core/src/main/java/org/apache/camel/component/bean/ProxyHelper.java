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

import java.lang.reflect.Proxy;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.processor.DeferServiceFactory;

/**
 * A helper class for creating proxies which delegate to Camel
 *
 * @version 
 */
public final class ProxyHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ProxyHelper() {
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxyObject(Endpoint endpoint, Producer producer, ClassLoader classLoader, Class<T>[] interfaces, MethodInfoCache methodCache) {
        return createProxyObject(endpoint, true, producer, classLoader, interfaces, methodCache);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxyObject(Endpoint endpoint, boolean binding, Producer producer, ClassLoader classLoader, Class<T>[] interfaces, MethodInfoCache methodCache) {
        return (T) Proxy.newProxyInstance(classLoader, interfaces.clone(), new CamelInvocationHandler(endpoint, binding, producer, methodCache));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T> interfaceClass, MethodInfoCache methodCache) throws Exception {
        return createProxy(endpoint, true, cl, toArray(interfaceClass), methodCache);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, boolean binding, ClassLoader cl, Class<T> interfaceClass, MethodInfoCache methodCache) throws Exception {
        return createProxy(endpoint, binding, cl, toArray(interfaceClass), methodCache);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T>[] interfaceClasses, MethodInfoCache methodCache) throws Exception {
        return createProxy(endpoint, true, cl, interfaceClasses, methodCache);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, boolean binding, ClassLoader cl, Class<T>[] interfaceClasses, MethodInfoCache methodCache) throws Exception {
        Producer producer = DeferServiceFactory.createProducer(endpoint);
        endpoint.getCamelContext().deferStartService(producer, true);
        return createProxyObject(endpoint, binding, producer, cl, interfaceClasses, methodCache);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T> interfaceClass) throws Exception {
        return createProxy(endpoint, true, cl, toArray(interfaceClass));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T>... interfaceClasses) throws Exception {
        return createProxy(endpoint, true, cl, interfaceClasses);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, boolean binding, ClassLoader cl, Class<T>... interfaceClasses) throws Exception {
        return createProxy(endpoint, binding, cl, interfaceClasses, createMethodInfoCache(endpoint));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, Class<T> interfaceClass) throws Exception {
        return createProxy(endpoint, true, toArray(interfaceClass));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxy(Endpoint endpoint, Class<T>... interfaceClasses) throws Exception {
        return createProxy(endpoint, true, interfaceClasses);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, boolean binding, Class<T>... interfaceClasses) throws Exception {
        return createProxy(endpoint, binding, getClassLoader(interfaceClasses), interfaceClasses);
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, Producer producer, Class<T> interfaceClass) throws Exception {
        return createProxy(endpoint, true, producer, toArray(interfaceClass));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     *
     * @deprecated use the same method name with binding as parameter
     */
    @Deprecated
    public static <T> T createProxy(Endpoint endpoint, Producer producer, Class<T>... interfaceClasses) throws Exception {
        return createProxyObject(endpoint, true, producer, getClassLoader(interfaceClasses), interfaceClasses, createMethodInfoCache(endpoint));
    }

    /**
     * Creates a Proxy which sends the exchange to the endpoint.
     */
    public static <T> T createProxy(Endpoint endpoint, boolean binding, Producer producer, Class<T>... interfaceClasses) throws Exception {
        return createProxyObject(endpoint, binding, producer, getClassLoader(interfaceClasses), interfaceClasses, createMethodInfoCache(endpoint));
    }

    /**
     * Returns the class loader of the first interface or throws {@link IllegalArgumentException} if there are no interfaces specified
     */
    protected static ClassLoader getClassLoader(Class<?>... interfaces) {
        if (interfaces == null || interfaces.length < 1) {
            throw new IllegalArgumentException("You must provide at least 1 interface class.");
        }
        return interfaces[0].getClassLoader();
    }

    protected static MethodInfoCache createMethodInfoCache(Endpoint endpoint) {
        return new MethodInfoCache(endpoint.getCamelContext());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T>[] toArray(Class<T> interfaceClass) {
        // this method and it's usage is introduced to avoid compiler warnings
        // about the generic Class arrays in the case we've got only one single
        // Class to build a Proxy for
        return new Class[] {interfaceClass};
    }
}
