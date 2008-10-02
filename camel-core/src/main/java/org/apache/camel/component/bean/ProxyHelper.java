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

/**
 * A helper class for creating proxies which delegate to Camel
 *
 * @version $Revision$
 */
public final class ProxyHelper {

    /**
     * Utility classes should not have a public constructor.
     */
    private ProxyHelper() {
    }


    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     */
    @SuppressWarnings("unchecked")
    public static Object createProxyObject(Endpoint endpoint, Producer producer, ClassLoader classLoader, Class[] interfaces, MethodInfoCache methodCache) {
        return Proxy.newProxyInstance(classLoader, interfaces.clone(), new CamelInvocationHandler(endpoint, producer, methodCache));
    }


    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class[] interfaces, MethodInfoCache methodCache) throws Exception {
        return (T) createProxyObject(endpoint, endpoint.createProducer(), cl, interfaces, methodCache);
    }

    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T>... interfaceClasses) throws Exception {
        return (T) createProxy(endpoint, cl, interfaceClasses, createMethodInfoCache(endpoint));
    }


    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, Class<T>... interfaceClasses) throws Exception {
        return (T) createProxy(endpoint, getClassLoader(interfaceClasses), interfaceClasses);
    }

    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, Producer producer, Class<T>... interfaceClasses) throws Exception {
        return (T) createProxyObject(endpoint, producer, getClassLoader(interfaceClasses), interfaceClasses, createMethodInfoCache(endpoint));
    }


    /**
     * Returns the class loader of the first interface or throws {@link IllegalArgumentException} if there are no interfaces specified
     *
     * @return the class loader
     */
    protected static ClassLoader getClassLoader(Class... interfaces) {
        if (interfaces == null || interfaces.length < 1) {
            throw new IllegalArgumentException("You must provide at least 1 interface class.");
        }
        return interfaces[0].getClassLoader();
    }


    protected static MethodInfoCache createMethodInfoCache(Endpoint endpoint) {
        return new MethodInfoCache(endpoint.getCamelContext());
    }

}
