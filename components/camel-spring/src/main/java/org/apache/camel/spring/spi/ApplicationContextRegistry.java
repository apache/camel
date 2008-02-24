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
package org.apache.camel.spring.spi;

import org.apache.camel.spi.Registry;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * A {@link Registry} implementation which looks up the objects in the Spring
 * {@link ApplicationContext}
 * 
 * @version $Revision$
 */
public class ApplicationContextRegistry implements Registry {
    private ApplicationContext applicationContext;

    public ApplicationContextRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public <T> T lookup(String name, Class<T> type) {
        try {
            Object value = applicationContext.getBean(name, type);
            return type.cast(value);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    public Object lookup(String name) {
        try {
            return applicationContext.getBean(name);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }
}
