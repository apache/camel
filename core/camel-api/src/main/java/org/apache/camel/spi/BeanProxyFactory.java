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

import org.apache.camel.Endpoint;

/**
 * A factory for creating a {@link java.lang.reflect.Proxy} for a bean.
 * <p/>
 * This requires to have camel-bean on the classpath.
 */
public interface BeanProxyFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "bean-proxy-factory";

    /**
     * Creates a proxy bean facaded with the interfaces that when invoked will send the data as a message to a Camel endpoint.
     *
     * @param endpoint  the endpoint to send to when the proxy is invoked
     * @param binding   whether to use bean parameter binding which would be needed if invoking a bean method with multiple parameters
     * @param interfaceClasses the interface(s) to use as bean facade
     * @throws Exception is thrown if error creating the proxy
     * @return the created bean proxy
     */
    <T> T  createProxy(Endpoint endpoint, boolean binding, Class<T>... interfaceClasses) throws Exception;

}
