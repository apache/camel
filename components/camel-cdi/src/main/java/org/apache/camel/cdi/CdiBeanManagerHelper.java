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
package org.apache.camel.cdi;

import java.util.Set;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

/**
 * To make looking up beans in CDI easier
 */
public final class CdiBeanManagerHelper {

    /**
     * To lookup a bean by a type
     */
    public static <T> T lookupBeanByType(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        if (!beans.isEmpty()) {
            Bean<?> bean = beanManager.resolve(beans);
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            Object result = beanManager.getReference(bean, type, creationalContext);
            if (result != null) {
                return type.cast(result);
            }
        }

        return null;
    }

    /**
     * To lookup a bean by a name
     */
    public static Object lookupBeanByName(BeanManager beanManager, String name) {
        return lookupBeanByNameAndType(beanManager, name, Object.class);
    }

    /**
     * To lookup a bean by name and type
     */
    public static <T> T lookupBeanByNameAndType(BeanManager beanManager, String name, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(name);
        if (!beans.isEmpty()) {
            Bean<?> bean = beanManager.resolve(beans);
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            Object result = beanManager.getReference(bean, type, creationalContext);
            if (result != null) {
                return type.cast(result);
            }
        }

        return null;
    }

}
