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
package org.apache.camel.component.bean;

import java.lang.reflect.Proxy;

import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Create a dynamic proxy for a given interface and endpoint that sends the parameter object to the endpoint and optionally
 * receives a reply. Unlike the ProxyHelper this works only with methods that have only one parameter.
 */
@Deprecated
public final class PojoProxyHelper {

    private PojoProxyHelper() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Endpoint endpoint, Class<?>... interfaceClasses) throws Exception {
        Producer producer = endpoint.createProducer();
        // ensure the producer is started
        ServiceHelper.startService(producer);
        return (T)Proxy.newProxyInstance(ProxyHelper.getClassLoader(interfaceClasses), interfaceClasses.clone(), new PojoMessageInvocationHandler(endpoint, producer));
    }
}
