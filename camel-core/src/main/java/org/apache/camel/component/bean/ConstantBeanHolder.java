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
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.util.CamelContextHelper;

/**
 * A constant (singleton) bean implementation of {@link BeanHolder}
 *
 * @version $Revision$
 */
public class ConstantBeanHolder implements BeanHolder {
    private final Object bean;
    private Processor processor;
    private BeanInfo beanInfo;

    public ConstantBeanHolder(Object bean, BeanInfo beanInfo) {
        this.bean = bean;
        this.beanInfo = beanInfo;
        try {
            this.processor = CamelContextHelper.convertTo(beanInfo.getCamelContext(), Processor.class, bean);
        } catch (NoTypeConversionAvailableException ex) {
            this.processor = null;
        }
    }

    public ConstantBeanHolder(Object bean, CamelContext context) {
        this(bean, new BeanInfo(context, bean.getClass()));
    }
    public ConstantBeanHolder(Object bean, CamelContext context, ParameterMappingStrategy parameterMappingStrategy) {
        this(bean, new BeanInfo(context, bean.getClass(), parameterMappingStrategy));
    }

    @Override
    public String toString() {
        return bean.toString();
    }

    public Object getBean()  {
        return bean;
    }

    public Processor getProcessor() {
        return processor;
    }

    public BeanInfo getBeanInfo() {
        return beanInfo;
    }
}
