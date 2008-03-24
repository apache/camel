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
     *
     * @throws Exception
     */
    public static Object createProxy(final Endpoint endpoint, ClassLoader cl, Class interfaces[])
        throws Exception {
        final Producer producer = endpoint.createProducer();
        return Proxy.newProxyInstance(cl, interfaces, new CamelInvocationHandler(endpoint, producer));
    }

    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    public static Object createProxy(Endpoint endpoint, Class interfaces[]) throws Exception {
        if (interfaces.length < 1) {
            throw new IllegalArgumentException("You must provide at least 1 interface class.");
        }
        return createProxy(endpoint, interfaces[0].getClassLoader(), interfaces);
    }

    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, ClassLoader cl, Class<T> interfaceClass)
        throws Exception {
        return (T)createProxy(endpoint, cl, new Class[] {interfaceClass});
    }

    /**
     * Creates a Proxy which sends PojoExchange to the endpoint.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, Class<T> interfaceClass) throws Exception {
        return (T)createProxy(endpoint, new Class[] {interfaceClass});
    }

}
