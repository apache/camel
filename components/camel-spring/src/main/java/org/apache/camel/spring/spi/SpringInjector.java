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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Injector;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A Spring implementation of {@link Injector} allowing Spring to be used to dependency inject newly created POJOs
 */
public class SpringInjector implements Injector {
    private final ConfigurableApplicationContext applicationContext;
    private int autowireMode = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;
    private boolean dependencyCheck;

    public SpringInjector(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        return newInstance(type, true);
    }

    @Override
    public <T> T newInstance(Class<T> type, String factoryMethod) {
        T answer = null;
        try {
            // lookup factory method
            Method fm = type.getMethod(factoryMethod);
            if (Modifier.isStatic(fm.getModifiers()) && Modifier.isPublic(fm.getModifiers()) && fm.getReturnType() == type) {
                Object obj = fm.invoke(null);
                answer = type.cast(obj);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException("Error invoking factory method: " + factoryMethod + " on class: " + type, e);
        }
        return answer;
    }

    @Override
    public <T> T newInstance(Class<T> type, boolean postProcessBean) {
        Object value;
        if (postProcessBean) {
            value = applicationContext.getBeanFactory().createBean(type, autowireMode, dependencyCheck);
        } else {
            value = applicationContext.getBeanFactory().createBean(type, AutowireCapableBeanFactory.AUTOWIRE_NO, false);
        }
        return type.cast(value);
    }

    @Override
    public boolean supportsAutoWiring() {
        return true;
    }

    public int getAutowireMode() {
        return autowireMode;
    }

    public void setAutowireMode(int autowireMode) {
        this.autowireMode = autowireMode;
    }

    public boolean isDependencyCheck() {
        return dependencyCheck;
    }

    public void setDependencyCheck(boolean dependencyCheck) {
        this.dependencyCheck = dependencyCheck;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
