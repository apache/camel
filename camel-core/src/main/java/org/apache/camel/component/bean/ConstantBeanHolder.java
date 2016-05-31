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
import org.apache.camel.Processor;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A constant (singleton) bean implementation of {@link BeanHolder}
 *
 * @version 
 */
public class ConstantBeanHolder implements BeanHolder {
    private final Object bean;
    private final BeanInfo beanInfo;
    private Processor processor;

    public ConstantBeanHolder(Object bean, BeanInfo beanInfo) {
        ObjectHelper.notNull(bean, "bean");
        ObjectHelper.notNull(beanInfo, "beanInfo");

        this.bean = bean;
        this.beanInfo = beanInfo;
    }

    public ConstantBeanHolder(Object bean, CamelContext context) {
        ObjectHelper.notNull(bean, "bean");

        this.bean = bean;
        this.beanInfo = new BeanInfo(context, bean.getClass());
    }

    public ConstantBeanHolder(Object bean, CamelContext context, ParameterMappingStrategy parameterMappingStrategy) {
        ObjectHelper.notNull(bean, "bean");

        this.bean = bean;
        this.beanInfo = new BeanInfo(context, bean.getClass(), parameterMappingStrategy);
    }

    @Override
    public String toString() {
        // avoid invoke toString on bean as it may be a remote proxy
        return ObjectHelper.className(bean) + "(" + ObjectHelper.getIdentityHashCode(bean) + ")";
    }

    public Object getBean()  {
        return bean;
    }

    public Processor getProcessor() {
        if (this.processor == null) {
            this.processor = CamelContextHelper.convertTo(beanInfo.getCamelContext(), Processor.class, bean);
        }
        return this.processor;
    }

    public boolean supportProcessor() {
        return true;
    }

    public BeanInfo getBeanInfo() {
        return beanInfo;
    }

    public BeanInfo getBeanInfo(Object bean) {
        return null;
    }
}
