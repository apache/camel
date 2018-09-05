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
package org.apache.camel.test.cdi;

import java.lang.reflect.Type;
import javax.enterprise.inject.spi.BeanManager;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

final class FrameworkMethodCdiInjection extends Statement {

    private final FrameworkMethod method;

    private final Object test;

    private final CamelCdiContext context;

    FrameworkMethodCdiInjection(FrameworkMethod method, Object test, CamelCdiContext context) {
        this.method = method;
        this.test = test;
        this.context = context;
    }

    @Override
    public void evaluate() throws Throwable {
        BeanManager manager = context.getBeanManager();
        Type[] types = method.getMethod().getGenericParameterTypes();
        Object[] parameters = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            // TODO: use a proper CreationalContext...
            parameters[i] = manager.getInjectableReference(new FrameworkMethodInjectionPoint(method.getMethod(), i, manager), manager.createCreationalContext(null));
        }

        method.invokeExplosively(test, parameters);
    }
}
