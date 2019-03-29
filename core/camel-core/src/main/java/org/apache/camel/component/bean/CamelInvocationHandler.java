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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;

/**
 * An {@link java.lang.reflect.InvocationHandler} which invokes a message
 * exchange on a camel {@link Endpoint}
 */
public class CamelInvocationHandler extends AbstractCamelInvocationHandler implements InvocationHandler {
    private final MethodInfoCache methodInfoCache;
    private final boolean binding;

    public CamelInvocationHandler(Endpoint endpoint, boolean binding, Producer producer, MethodInfoCache methodInfoCache) {
        super(endpoint, producer);
        this.binding = binding;
        this.methodInfoCache = methodInfoCache;
    }

    @Override
    public Object doInvokeProxy(Object proxy, Method method, Object[] args) throws Throwable {
        MethodInfo methodInfo = methodInfoCache.getMethodInfo(method);
        final ExchangePattern pattern = methodInfo != null ? methodInfo.getPattern() : ExchangePattern.InOut;
        return invokeProxy(method, pattern, args, binding);
    }

}
