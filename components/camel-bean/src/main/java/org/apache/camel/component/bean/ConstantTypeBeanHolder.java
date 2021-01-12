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
import org.apache.camel.Processor;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A constant (singleton) bean implementation of {@link org.apache.camel.component.bean.BeanTypeHolder}
 */
public class ConstantTypeBeanHolder implements BeanTypeHolder {
    private final Class<?> type;
    private final BeanInfo beanInfo;
    private Map<String, Object> options;

    public ConstantTypeBeanHolder(Class<?> type, CamelContext context, ParameterMappingStrategy parameterMappingStrategy,
                                  BeanComponent beanComponent) {
        this(type, new BeanInfo(context, type, parameterMappingStrategy, beanComponent));
    }

    public ConstantTypeBeanHolder(Class<?> type, BeanInfo beanInfo) {
        ObjectHelper.notNull(type, "type");
        ObjectHelper.notNull(beanInfo, "beanInfo");

        this.type = type;
        this.beanInfo = beanInfo;
    }

    @Override
    public void setErrorHandler(Processor errorHandler) {
        for (MethodInfo mi : beanInfo.getMethods()) {
            mi.setErrorHandler(errorHandler);
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
     * Creates a cached and constant {@link org.apache.camel.component.bean.BeanHolder} from this holder.
     *
     * @return a new {@link org.apache.camel.component.bean.BeanHolder} that has cached the lookup of the bean.
     */
    public ConstantBeanHolder createCacheHolder() {
        Object bean = getBean(null);
        return new ConstantBeanHolder(bean, beanInfo);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    @Override
    public Object getBean(Exchange exchange) {
        // only create a bean if we have a default no-arg constructor
        if (beanInfo.hasPublicNoArgConstructors()) {
            Object bean = getBeanInfo().getCamelContext().getInjector().newInstance(type, false);
            if (options != null && !options.isEmpty()) {
                PropertyBindingSupport.build()
                        .withRemoveParameters(false)
                        .withCamelContext(getBeanInfo().getCamelContext())
                        .withProperties(options)
                        .withTarget(bean)
                        .bind();
            }
            return bean;
        } else {
            return null;
        }
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
        return beanInfo;
    }

    @Override
    public BeanInfo getBeanInfo(Object bean) {
        return null;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

}
