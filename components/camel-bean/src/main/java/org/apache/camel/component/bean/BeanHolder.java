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
 * Object holder for a bean.
 */
public interface BeanHolder {

    /**
     * Gets the bean.
     *
     * @throws NoSuchBeanException is thrown if the bean cannot be found.
     */
    Object getBean(Exchange exchange) throws NoSuchBeanException;

    /**
     * Gets a {@link Processor} for this bean, if supported.
     *
     * @return the {@link Processor}, or <tt>null</tt> if not supported.
     */
    Processor getProcessor();

    /**
     * Whether a {@link Processor} is supported by this bean holder.
     *
     * @return <tt>true</tt> if the holder can supporting using a processor, <tt>false</tt> otherwise
     */
    boolean supportProcessor();

    /**
     * Gets bean info for the bean.
     */
    BeanInfo getBeanInfo();

    /**
     * Gets bean info for the given bean.
     * <p/>
     * This implementation allows a thread safe usage for {@link BeanHolder} implementations
     * such as the {@link RegistryBean}.
     *
     * @param bean the bean
     * @return <tt>null</tt> if not supported, then use {@link #getBeanInfo()} instead.
     */
    BeanInfo getBeanInfo(Object bean);
}
