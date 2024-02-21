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

import java.lang.reflect.Method;
import java.util.Set;

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.BeanProcessorFactory;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService(BeanProcessorFactory.FACTORY)
public final class DefaultBeanProcessorFactory extends ServiceSupport
        implements BeanProcessorFactory, CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultBeanProcessorFactory.class);

    private CamelContext camelContext;
    private ParameterMappingStrategy parameterMappingStrategy;
    private BeanComponent beanComponent;

    public DefaultBeanProcessorFactory() {
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Processor createBeanProcessor(CamelContext camelContext, Object bean, Method method) throws Exception {
        BeanInfo info
                = new BeanInfo(camelContext, method.getDeclaringClass(), bean, method, parameterMappingStrategy, beanComponent);
        return new BeanProcessor(bean, info);
    }

    @Override
    public Processor createBeanProcessor(
            CamelContext camelContext, Object bean, String beanType, Class<?> beanClass, String ref,
            String method, BeanScope scope)
            throws Exception {

        BeanProcessor answer;
        Class<?> clazz = bean != null ? bean.getClass() : null;
        BeanHolder beanHolder;

        if (ObjectHelper.isNotEmpty(ref)) {
            if (scope == BeanScope.Singleton) {
                // cache the registry lookup which avoids repeat lookup in the registry
                beanHolder = new RegistryBean(camelContext, ref, parameterMappingStrategy, beanComponent)
                        .createCacheHolder();
                // bean holder will check if the bean exists
                bean = beanHolder.getBean(null);
            } else {
                // we do not cache so we invoke on-demand
                beanHolder = new RegistryBean(camelContext, ref, parameterMappingStrategy, beanComponent);
            }
            if (scope == BeanScope.Request) {
                // wrap in registry scoped holder
                beanHolder = new RequestBeanHolder(beanHolder);
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
                        clazz = camelContext.getClassResolver().resolveMandatoryClass(beanType);
                    } catch (ClassNotFoundException e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                } else {
                    clazz = beanClass;
                }

                if (scope == BeanScope.Singleton && clazz != null) {
                    // attempt to lookup in registry by type to favour using it (like bean ref would do to lookup in registry)
                    Set<?> beans = camelContext.getRegistry().findByType(clazz);
                    if (!beans.isEmpty()) {
                        if (beans.size() == 1) {
                            LOG.debug("Exactly one instance of type: {} in registry found.", clazz);
                            bean = beans.iterator().next();
                        } else {
                            LOG.debug("Found {} bean instances of type: {} in the registry.", beans.size(), clazz);
                        }
                    }
                }

                // attempt to create bean using injector which supports auto-wiring
                if (bean == null && scope == BeanScope.Singleton && camelContext.getInjector().supportsAutoWiring()) {
                    try {
                        LOG.debug("Attempting to create new bean instance from class: {} via auto-wiring enabled", clazz);
                        bean = CamelContextHelper.newInstance(camelContext, clazz);
                    } catch (Exception e) {
                        LOG.debug("Error creating new bean instance from class: {}. This exception is ignored", clazz, e);
                    }
                }

                // create a bean if there is a default public no-arg constructor
                if (bean == null && scope == BeanScope.Singleton && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    LOG.debug("Class has default no-arg constructor so creating a new bean instance: {}", clazz);
                    bean = CamelContextHelper.newInstance(camelContext, clazz);
                    ObjectHelper.notNull(bean, "bean", this);
                }
            }

            // validate the bean type is not from java so you by mistake think its a reference
            // to a bean name but the String is being invoke instead
            if (bean instanceof String) {
                throw new IllegalArgumentException(
                        "The bean instance is a java.lang.String type: " + bean
                                                   + ". We suppose you want to refer to a bean instance by its id instead. Please use ref.");
            }

            // the holder should either be bean or type based
            if (bean != null) {
                beanHolder = new ConstantBeanHolder(bean, camelContext, parameterMappingStrategy, beanComponent);
            } else {
                if (scope == BeanScope.Singleton && ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                    // we can only cache if we can create an instance of the bean, and for that we need a public constructor
                    beanHolder = new ConstantTypeBeanHolder(clazz, camelContext, parameterMappingStrategy, beanComponent)
                            .createCacheHolder();
                } else {
                    if (ObjectHelper.hasDefaultPublicNoArgConstructor(clazz)) {
                        beanHolder = new ConstantTypeBeanHolder(clazz, camelContext, parameterMappingStrategy, beanComponent);
                    } else if (clazz.isInterface()) {
                        throw new IllegalArgumentException(
                                "The bean is an interface type: " + clazz
                                                           + ". Interfaces are only supported to lookup in the Camel registry for a single instance of such type."
                                                           + " Otherwise the bean must be a class type.");
                    } else {
                        // this is only for invoking static methods on the bean
                        beanHolder = new ConstantStaticTypeBeanHolder(
                                clazz, camelContext, parameterMappingStrategy, beanComponent);
                    }
                }
            }
            if (scope == BeanScope.Request) {
                // wrap in registry scoped holder
                beanHolder = new RequestBeanHolder(beanHolder);
            }
            answer = new BeanProcessor(beanHolder);
        }

        // check for method exists
        if (method != null) {
            answer.setMethod(method);

            // check there is a method with the given name, and leverage BeanInfo for that
            // which we only do if we are caching the bean as otherwise we will create a bean instance for this check
            // which we only want to do if we cache the bean
            if (scope == BeanScope.Singleton) {
                BeanInfo beanInfo = beanHolder.getBeanInfo();
                if (bean != null) {
                    // there is a bean instance, so check for any methods
                    if (!beanInfo.hasMethod(method)) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(new MethodNotFoundException(null, bean, method));
                    }
                } else if (clazz != null) {
                    // there is no bean instance, so check for static methods only
                    if (!beanInfo.hasStaticMethod(method)) {
                        throw RuntimeCamelException
                                .wrapRuntimeCamelException(new MethodNotFoundException(null, clazz, method, true));
                    }
                }
            }
        }

        return answer;
    }

    @Override
    protected void doInit() throws Exception {
        parameterMappingStrategy = ParameterMappingStrategyHelper.createParameterMappingStrategy(getCamelContext());

        beanComponent = getCamelContext().getComponent("bean", BeanComponent.class);
        ServiceHelper.initService(beanComponent);
    }
}
