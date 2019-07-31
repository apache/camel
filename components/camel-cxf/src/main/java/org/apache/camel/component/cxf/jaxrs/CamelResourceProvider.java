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
package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Proxy;

import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.message.Message;

public class CamelResourceProvider implements ResourceProvider {
    // Using the dynamical proxy to provide the instance of client to invoke
    private Class<?> clazz;
    private ResourceProvider provider;
    
    public CamelResourceProvider(Class<?> clazz) {
        this.clazz = clazz;
        if (!clazz.isInterface()) {
            provider = new PerRequestResourceProvider(clazz);
        }
    }

    @Override
    public Object getInstance(Message m) {
        Object result = null;
        if (provider != null) {
            result = provider.getInstance(m);
        } else {
            // create the instance with the invoker
            result = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {clazz},
                                            new SubResourceClassInvocationHandler());
        }
        return result;
    }

    @Override
    public void releaseInstance(Message m, Object o) {
        if (provider != null) {
            provider.releaseInstance(m, o);
        }
    }

    @Override
    public Class<?> getResourceClass() {
        if (provider != null) {
            return provider.getResourceClass();
        } else {
            return clazz;
        }
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

}
