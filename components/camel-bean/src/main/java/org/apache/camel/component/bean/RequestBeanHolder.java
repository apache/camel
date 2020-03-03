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

import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;

/**
 * Request scoped {@link BeanHolder} wrapper.
 */
public class RequestBeanHolder implements BeanHolder {

    private final BeanHolder holder;
    private final String key;

    public RequestBeanHolder(BeanHolder holder) {
        this.holder = holder;
        this.key = "CamelBeanRequestScope-" + holder.getBeanInfo().getType().getName();
    }

    @Override
    public Object getBean(Exchange exchange) throws NoSuchBeanException {
        Object bean = exchange.getProperty(key);
        if (bean == null) {
            bean = holder.getBean(exchange);
            exchange.setProperty(key, bean);
        }
        return bean;
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
        return holder.getBeanInfo();
    }

    @Override
    public BeanInfo getBeanInfo(Object bean) {
        return holder.getBeanInfo(bean);
    }
}
