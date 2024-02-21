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
package org.apache.camel.spring.spi;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.BeanRepository;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.context.ApplicationContext;

/**
 * A {@link BeanRepository} implementation which looks up the objects in the Spring {@link ApplicationContext}
 */
public class ApplicationContextBeanRepository implements BeanRepository {
    private ApplicationContext applicationContext;

    public ApplicationContextBeanRepository(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object answer;
        try {
            if (applicationContext.containsBean(name)) {
                answer = applicationContext.getBean(name, type);
            } else {
                return null;
            }
        } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException e) {
            return null;
        }

        try {
            return type.cast(answer);
        } catch (Exception e) {
            String msg = "Found bean: " + name + " in ApplicationContext: " + applicationContext
                         + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    @Override
    public Object lookupByName(String name) {
        try {
            if (applicationContext.containsBean(name)) {
                return applicationContext.getBean(name);
            } else {
                return null;
            }
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Map<String, T> map = findByTypeWithName(type);
        return new HashSet<>(map.values());
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        return BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, type);
    }

    @Override
    public <T> T findSingleByType(Class<T> type) {
        try {
            // this API allows to support @Primary beans that should take precedence in
            // case there are 2+ beans of the same type.
            NamedBeanHolder<T> holder = applicationContext.getAutowireCapableBeanFactory().resolveNamedBean(type);
            return holder.getBeanInstance();
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

}
