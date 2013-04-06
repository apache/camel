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
package org.apache.camel.component.cxf.jaxrs;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.cxf.jaxrs.utils.ResourceUtils;
// This class only return the sub class instance
public class SubResourceClassInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] parameters) throws Throwable {
        Object result = null;
        Class<?> returnType = method.getReturnType();
        if (!returnType.isAssignableFrom(Void.class)) {
            // create a instance to return
            if (returnType.isInterface()) {
                // create a new proxy for it
                result = Proxy.newProxyInstance(returnType.getClassLoader(), new Class[] {returnType},
                                                new SubResourceClassInvocationHandler());
            } else {
                // get the constructor and create a new instance
                Constructor<?> c = ResourceUtils.findResourceConstructor(returnType, true);
                result = c.newInstance(new Object[] {});
            }
        }
        return result;
    }

}
