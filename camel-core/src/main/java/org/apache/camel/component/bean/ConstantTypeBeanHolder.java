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
import org.apache.camel.util.ObjectHelper;

/**
 * A constant (singleton) bean implementation of {@link org.apache.camel.component.bean.BeanTypeHolder}
 *
 * @version
 */
public class ConstantTypeBeanHolder implements BeanTypeHolder {
    private final Class<?> type;
    private final BeanInfo beanInfo;

    public ConstantTypeBeanHolder(Class<?> type, BeanInfo beanInfo) {
        ObjectHelper.notNull(type, "type");
        ObjectHelper.notNull(beanInfo, "beanInfo");

        this.type = type;
        this.beanInfo = beanInfo;
    }

    public ConstantTypeBeanHolder(Class<?> type, CamelContext context) {
        this(type, new BeanInfo(context, type));
    }

    public ConstantTypeBeanHolder(Class<?> type, CamelContext context, ParameterMappingStrategy parameterMappingStrategy) {
        this(type, new BeanInfo(context, type, parameterMappingStrategy));
    }

    /**
     * Creates a cached and constant {@link org.apache.camel.component.bean.BeanHolder} from this holder.
     *
     * @return a new {@link org.apache.camel.component.bean.BeanHolder} that has cached the lookup of the bean.
     */
    public ConstantBeanHolder createCacheHolder() throws Exception {
        Object bean = getBean();
        return new ConstantBeanHolder(bean, beanInfo);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    public Object getBean()  {
        // only create a bean if we have constructors
        if (beanInfo.hasPublicConstructors()) {
            return getBeanInfo().getCamelContext().getInjector().newInstance(type);
        } else {
            return null;
        }
    }

    public Processor getProcessor() {
        return null;
    }

    public boolean supportProcessor() {
        return false;
    }

    public BeanInfo getBeanInfo() {
        return beanInfo;
    }

    public BeanInfo getBeanInfo(Object bean) {
        return null;
    }

    public Class<?> getType() {
        return type;
    }

}
