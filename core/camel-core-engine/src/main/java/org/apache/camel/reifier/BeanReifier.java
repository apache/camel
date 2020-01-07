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
package org.apache.camel.reifier;

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

public class BeanReifier extends ProcessorReifier<BeanDefinition> {

    public BeanReifier(ProcessorDefinition<?> definition) {
        super(BeanDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        CamelContext camelContext = routeContext.getCamelContext();

        Object bean = definition.getBean();
        String ref = definition.getRef();
        String method = definition.getMethod();
        String beanType = definition.getBeanType();
        Class<?> beanClass = definition.getBeanClass();

        BeanProcessorFactory fac = camelContext.adapt(ExtendedCamelContext.class).getBeanProcessorFactory();
        if (fac == null) {
            throw new IllegalStateException("Cannot find BeanProcessorFactory. Make sure camel-bean is on the classpath.");
        }
        // use singleton as default scope
        BeanScope scope = BeanScope.Singleton;
        if (definition.getScope() != null) {
            scope = CamelContextHelper.parse(routeContext.getCamelContext(), BeanScope.class, definition.getScope());
        }
        return fac.createBeanProcessor(camelContext, bean, beanType, beanClass, ref, method, scope);
    }

}
