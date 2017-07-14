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

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.spi.Registry;

/**
 * An implementation of a {@link BeanHolder} which will look up a bean from the registry and act as a cache of its metadata
 *
 * @version 
 */
public class RegistryBean implements BeanHolder {
    private final CamelContext context;
    private final String name;
    private final Registry registry;
    private volatile BeanInfo beanInfo;
    private volatile Class<?> clazz;
    private ParameterMappingStrategy parameterMappingStrategy;

    public RegistryBean(CamelContext context, String name) {
        this(context.getRegistry(), context, name);
    }

    public RegistryBean(Registry registry, CamelContext context, String name) {
        this.registry = registry;
        this.context = context;
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

    /**
     * Creates a cached and constant {@link org.apache.camel.component.bean.BeanHolder} from this holder.
     *
     * @return a new {@link org.apache.camel.component.bean.BeanHolder} that has cached the lookup of the bean.
     */
    public ConstantBeanHolder createCacheHolder() {
        Object bean = getBean();
        BeanInfo info = createBeanInfo(bean);
        return new ConstantBeanHolder(bean, info);
    }

    public Object getBean() throws NoSuchBeanException {
        // must always lookup bean first
        Object value = lookupBean();

        if (value != null) {
            // could be a class then create an instance of it
            if (value instanceof Class) {
                // bean is a class so create an instance of it
                value = context.getInjector().newInstance((Class<?>)value);
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

    public Processor getProcessor() {
        return null;
    }

    public boolean supportProcessor() {
        return false;
    }

    public BeanInfo getBeanInfo() {
        if (beanInfo == null) {
            Object bean = getBean();
            this.beanInfo = createBeanInfo(bean);
        }
        return beanInfo;
    }

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

    public ParameterMappingStrategy getParameterMappingStrategy() {
        if (parameterMappingStrategy == null) {
            parameterMappingStrategy = createParameterMappingStrategy();
        }
        return parameterMappingStrategy;
    }

    public void setParameterMappingStrategy(ParameterMappingStrategy parameterMappingStrategy) {
        this.parameterMappingStrategy = parameterMappingStrategy;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected BeanInfo createBeanInfo(Object bean) {
        return new BeanInfo(context, bean.getClass(), getParameterMappingStrategy());
    }

    protected ParameterMappingStrategy createParameterMappingStrategy() {
        return BeanInfo.createParameterMappingStrategy(context);
    }

    protected Object lookupBean() {
        return registry.lookupByName(name);
    }
}
