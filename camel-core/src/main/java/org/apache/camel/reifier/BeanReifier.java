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
package org.apache.camel.reifier;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanInfo;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.bean.ConstantBeanHolder;
import org.apache.camel.component.bean.ConstantStaticTypeBeanHolder;
import org.apache.camel.component.bean.ConstantTypeBeanHolder;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.component.bean.RegistryBean;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

class BeanReifier extends ProcessorReifier<BeanDefinition> {

    BeanReifier(ProcessorDefinition<?> definition) {
        super(BeanDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Object bean = definition.getBean();
        String ref = definition.getRef();
        String method = definition.getMethod();
        String beanType = definition.getBeanType();
        Class<?> beanClass = definition.getBeanClass();

        BeanProcessor answer;
        Class<?> clazz = bean != null ? bean.getClass() : null;
        BeanHolder beanHolder;

        if (ObjectHelper.isNotEmpty(ref)) {
            // lets cache by default
            if (isCacheBean()) {
                // cache the registry lookup which avoids repeat lookup in the registry
                beanHolder = new RegistryBean(routeContext.getCamelContext(), ref).createCacheHolder();
                // bean holder will check if the bean exists
                bean = beanHolder.getBean();
            } else {
                // we do not cache so we invoke on-demand
                beanHolder = new RegistryBean(routeContext.getCamelContext(), ref);
            }
            answer = new BeanProcessor(beanHolder);
        } else {
            if (bean == null) {

                if (beanType == null && beanClass == null) {
                    throw new IllegalArgumentException("bean, ref or beanType must be provided");
                }

                // the clazz is either from beanType or beanClass
                if (beanType != null) {
                    try {
                        clazz = routeContext.getCamelContext().getClassResolver().resolveMandatoryClass(beanType);
                    } catch (ClassNotFoundException e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                } else {
                    clazz = beanClass;
                }

                // attempt to create bean using injector which supports auto-wiring
                if (isCacheBean() && routeContext.getCamelContext().getInjector().supportsAutoWiring()) {
                    try {
                        log.debug("Attempting to create new bean instance from class: {} via auto-wiring enabled", clazz);
                        bean = CamelContextHelper.newInstance(routeContext.getCamelContext(), clazz);
                    } catch (Throwable e) {
                        log.debug("Error creating new bean instance from class: " + clazz + ". This exception is ignored", e);
                    }
                }

                // create a bean if there is a default public no-arg constructor
                if (bean == null && isCacheBean() && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    log.debug("Class has default no-arg constructor so creating a new bean instance: {}", clazz);
                    bean = CamelContextHelper.newInstance(routeContext.getCamelContext(), clazz);
                    ObjectHelper.notNull(bean, "bean", this);
                }
            }

            // validate the bean type is not from java so you by mistake think its a reference
            // to a bean name but the String is being invoke instead
            if (bean instanceof String) {
                throw new IllegalArgumentException("The bean instance is a java.lang.String type: " + bean
                        + ". We suppose you want to refer to a bean instance by its id instead. Please use ref.");
            }

            // the holder should either be bean or type based
            if (bean != null) {
                beanHolder = new ConstantBeanHolder(bean, routeContext.getCamelContext());
            } else {
                if (isCacheBean() && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    // we can only cache if we can create an instance of the bean, and for that we need a public constructor
                    beanHolder = new ConstantTypeBeanHolder(clazz, routeContext.getCamelContext()).createCacheHolder();
                } else {
                    if (ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                        beanHolder = new ConstantTypeBeanHolder(clazz, routeContext.getCamelContext());
                    } else {
                        // this is only for invoking static methods on the bean
                        beanHolder = new ConstantStaticTypeBeanHolder(clazz, routeContext.getCamelContext());
                    }
                }
            }
            answer = new BeanProcessor(beanHolder);
        }

        // check for method exists
        if (method != null) {
            answer.setMethod(method);

            // check there is a method with the given name, and leverage BeanInfo for that
            // which we only do if we are caching the bean as otherwise we will create a bean instance for this check
            // which we only want to do if we cache the bean
            if (isCacheBean()) {
                BeanInfo beanInfo = beanHolder.getBeanInfo();
                if (bean != null) {
                    // there is a bean instance, so check for any methods
                    if (!beanInfo.hasMethod(method)) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
                    }
                } else if (clazz != null) {
                    // there is no bean instance, so check for static methods only
                    if (!beanInfo.hasStaticMethod(method)) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(new MethodNotFoundException(null, clazz, method, true));
                    }
                }
            }
        }

        return answer;
    }

    private boolean isCacheBean() {
        return definition.getCache() == null || definition.getCache();
    }

}
