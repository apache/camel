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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.BeanProxyFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A build to create Camel proxies.
 */
public final class ProxyBuilder {

    private final CamelContext camelContext;
    private Endpoint endpoint;
    private boolean binding = true;

    public ProxyBuilder(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Send the proxied message to this endpoint
     *
     * @param  url uri of endpoint
     * @return     the builder
     */
    public ProxyBuilder endpoint(String url) {
        this.endpoint = camelContext.getEndpoint(url);
        return this;
    }

    /**
     * Send the proxied message to this endpoint
     *
     * @param  endpoint the endpoint
     * @return          the builder
     */
    public ProxyBuilder endpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * Builds the proxy.
     *
     * @param  interfaceClass the service interface
     * @return                the proxied bean
     * @throws Exception      is thrown if error creating the proxy
     */
    @SuppressWarnings("unchecked")
    public <T> T build(Class<T> interfaceClass) throws Exception {
        // this method is introduced to avoid compiler warnings about the
        // generic Class arrays in the case we've got only one single Class
        // to build a Proxy for
        return build((Class<T>[]) new Class[] { interfaceClass });
    }

    /**
     * Builds the proxy.
     *
     * @param  interfaceClasses the service interface(s)
     * @return                  the proxied bean
     * @throws Exception        is thrown if error creating the proxy
     */
    public <T> T build(Class<T>... interfaceClasses) throws Exception {
        ObjectHelper.notNull(endpoint, "endpoint");
        // use proxy service
        BeanProxyFactory factory = PluginHelper.getBeanProxyFactory(camelContext);
        return factory.createProxy(endpoint, binding, interfaceClasses);
    }

}
