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
package org.apache.camel.cdi;

import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.CamelContext;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;

import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.camel.util.ObjectHelper.isEmpty;

final class XmlFactoryBeanInjectionTarget<T> extends SyntheticInjectionTarget<T> {

    XmlFactoryBeanInjectionTarget(BeanManager manager, AbstractCamelFactoryBean<T> factory, Bean<?> context) {
        super(
            () -> {
                try {
                    if (isEmpty(factory.getCamelContextId()) && context != null) {
                        factory.setCamelContext(getReference(manager, CamelContext.class, context));
                    }
                    factory.afterPropertiesSet();
                    return factory.getObject();
                } catch (Exception cause) {
                    throw new CreationException(cause);
                }
            },
            i -> {
            },
            i -> {
                try {
                    factory.destroy();
                } catch (Exception cause) {
                    throw new InjectionException(cause);
                }
            }
        );
    }
}
