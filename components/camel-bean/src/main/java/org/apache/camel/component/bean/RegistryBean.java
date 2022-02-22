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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.PropertyBindingSupport;

/**
 * An implementation of a {@link BeanHolder} which will look up a bean from the registry and act as a cache of its
 * metadata
 */
public class RegistryBean implements BeanHolder {
    private final Registry registry;
    private final CamelContext context;
    private final String name;
    private final ParameterMappingStrategy parameterMappingStrategy;
    private final BeanComponent beanComponent;
    private volatile BeanInfo beanInfo;
    private volatile Processor errorHandler;
    private volatile Class<?> clazz;
    private Map<String, Object> options;

    public RegistryBean(CamelContext context, String name,
                        ParameterMappingStrategy parameterMappingStrategy, BeanComponent beanComponent) {
        this.registry = context.getRegistry();
        this.context = context;
        this.parameterMappingStrategy = parameterMappingStrategy != null
                ? parameterMappingStrategy : ParameterMappingStrategyHelper.createParameterMappingStrategy(context);
        this.beanComponent = beanComponent != null ? beanComponent : context.getComponent("bean", BeanComponent.class);
        if (name != null) {
            // for ref it may have "ref:" or "bean:" as prefix by mistake
            if (name.startsWith("ref:")) {
                this.name = name.substring(4);
            } else if (name.startsWith("bean:")) {
                this.name = name.substring(5);
            } else {
                this.name = name;
            }
        } else {
            this.name = null;
        }
    }

    @Override
    public String toString() {
        return "bean: " + name;
    }

    @Override
    public void setErrorHandler(Processor errorHandler) {
        if (beanInfo != null) {
            for (MethodInfo mi : beanInfo.getMethods()) {
                mi.setErrorHandler(errorHandler);
            }
        } else {
            // need to store it temporary until bean info is created
            this.errorHandler = errorHandler;
        }
    }

    @Override
    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    /**
     * Creates a singleton (cached and constant) {@link org.apache.camel.component.bean.BeanHolder} from this holder.
     */
    public ConstantBeanHolder createCacheHolder() {
        Object bean = getBean(null);
        BeanInfo info = createBeanInfo(bean);
        return new ConstantBeanHolder(bean, info);
    }

    @Override
    public Object getBean(Exchange exchange) throws NoSuchBeanException {
        Object bean = doGetBean();
        if (options != null && !options.isEmpty()) {
            PropertyBindingSupport.build()
                    .withRemoveParameters(false)
                    .withCamelContext(getBeanInfo().getCamelContext())
                    .withProperties(options)
                    .withTarget(bean)
                    .bind();
        }
        return bean;
    }

    private Object doGetBean() throws NoSuchBeanException {
        // must always lookup bean first
        Object value = lookupBean();

        if (value != null) {
            // could be a class then create an instance of it
            if (value instanceof Class) {
                // bean is a class so create an instance of it
                value = context.getInjector().newInstance((Class<?>) value);
            }
            return value;
        }

        // okay bean is not in registry, so try to resolve if its a class name and create a shared instance
        if (clazz == null) {
            clazz = context.getClassResolver().resolveClass(name);
        }

        if (clazz == null) {
            // no its not a class then we cannot find the bean
            throw new NoSuchBeanException(name);
        }

        // bean is a class so create an instance of it
        return context.getInjector().newInstance(clazz);
    }

    @Override
    public Processor getProcessor() {
        return null;
    }

    @Override
    public boolean supportProcessor() {
        return false;
    }

    @Override
    public BeanInfo getBeanInfo() {
        if (beanInfo == null) {
            Object bean = getBean(null);
            this.beanInfo = createBeanInfo(bean);
        }
        return beanInfo;
    }

    @Override
    public BeanInfo getBeanInfo(Object bean) {
        return createBeanInfo(bean);
    }

    public String getName() {
        return name;
    }

    public Registry getRegistry() {
        return registry;
    }

    public CamelContext getContext() {
        return context;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected BeanInfo createBeanInfo(Object bean) {
        BeanInfo bi = new BeanInfo(context, bean.getClass(), parameterMappingStrategy, beanComponent);
        if (errorHandler != null) {
            for (MethodInfo mi : bi.getMethods()) {
                mi.setErrorHandler(errorHandler);
            }
            errorHandler = null;
        }
        return bi;
    }

    protected Object lookupBean() {
        return registry.lookupByName(name);
    }
}
