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
package org.apache.camel.guice.impl;

import java.lang.reflect.Method;

import com.google.common.base.Objects;
import com.google.inject.Inject;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.ConstructorInvocation;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.CamelPostProcessorHelper;

/**
 * @version $Revision$
 */
public class ConsumerInjection extends CamelPostProcessorHelper implements ConstructorInterceptor {
    public Object construct(ConstructorInvocation invocation) throws Throwable {
        Object object = invocation.proceed();
        if (object != null) {
            Class<?> type = object.getClass();
            Method[] methods = type.getMethods();
            for (Method method : methods) {
                consumerInjection(method, object);
            }
        }
        return object;

    }

    @Override
    public CamelContext getCamelContext() {
        CamelContext context = super.getCamelContext();
        Objects.nonNull(context, "CamelContext not injected!");
        return context;
    }

    @Inject
    @Override
    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);
    }
}
