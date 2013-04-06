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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.guice.support.MethodHandler;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class ConsumerInjection<I> extends CamelPostProcessorHelper implements MethodHandler<I, Consume> {

    public void afterInjection(I injectee, Consume consume, Method method) throws InvocationTargetException, IllegalAccessException {
        consumerInjection(method, injectee, null);
    }

    @Override
    public CamelContext getCamelContext() {
        CamelContext context = super.getCamelContext();
        ObjectHelper.notNull(context, "CamelContext not injected");
        return context;
    }

    @Inject
    @Override
    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);
    }

}
