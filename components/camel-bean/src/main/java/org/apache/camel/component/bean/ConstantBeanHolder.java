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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A constant (singleton) bean implementation of {@link BeanHolder}
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

    @Override
    public String toString() {
        // avoid invoke toString on bean as it may be a remote proxy
        return ObjectHelper.className(bean) + "(" + ObjectHelper.getIdentityHashCode(bean) + ")";
    }

    @Override
    public Object getBean(Exchange exchange)  {
        return bean;
    }

    @Override
    public Processor getProcessor() {
        if (this.processor == null) {
            this.processor = CamelContextHelper.convertTo(beanInfo.getCamelContext(), Processor.class, bean);
        }
        return this.processor;
    }

    @Override
    public boolean supportProcessor() {
        return true;
    }

    @Override
    public BeanInfo getBeanInfo() {
        return beanInfo;
    }

    @Override
    public BeanInfo getBeanInfo(Object bean) {
        return null;
    }
}
