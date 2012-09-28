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

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;

/**
 * Special {@link java.lang.reflect.InvocationHandler} for methods that have only one parameter. This
 * parameter is directly sent to as the body of the message. The idea is to use
 * that as a very open message format especially when combined with e.g. JAXB
 * serialization.
 */
public class PojoMessageInvocationHandler extends AbstractCamelInvocationHandler {

    public PojoMessageInvocationHandler(Endpoint endpoint, Producer producer) {
        super(endpoint, producer);
    }

    @Override
    public Object doInvokeProxy(Object proxy, Method method, Object[] args) throws Throwable {
        int argsLength = (args == null) ? 0 : args.length;
        if (argsLength != 1) {
            throw new RuntimeCamelException(String.format("Error creating proxy for %s.%s Number of arguments must be 1 but is %d", 
                                                          method.getDeclaringClass().getName(),
                                                          method.getName(), argsLength));
        }
        final ExchangePattern pattern = method.getReturnType() != Void.TYPE ? ExchangePattern.InOut : ExchangePattern.InOnly;
        return invokeWithBody(method, args[0], pattern);
    }

}
