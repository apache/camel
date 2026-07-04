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
package org.apache.camel.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

/**
 * Pluggable injector that participates in {@link CamelBeanPostProcessor} bean post-processing to support custom,
 * typically 3rd-party, dependency-injection annotations.
 * <p/>
 * When the {@link CamelBeanPostProcessor} scans a bean it invokes every registered injector for each field
 * ({@link #onFieldInject(Field, Object, String)}) and method ({@link #onMethodInject(Method, Object, String)}),
 * allowing the injector to detect its own annotations and inject the appropriate values. This is how integrations layer
 * support for annotations beyond the built-in Camel ones (for example Spring or Quarkus annotations) on top of the
 * standard post processor. Injectors are registered through
 * {@link CamelBeanPostProcessor#addCamelBeanPostProjectInjector(CamelBeanPostProcessorInjector)}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/bean-injection.html">Bean Injection</a> in the Camel user manual.
 *
 * @see   CamelBeanPostProcessor
 * @since 3.16
 */
public interface CamelBeanPostProcessorInjector {

    /**
     * Field injection
     *
     * @param field    the field
     * @param bean     the bean instance where the field is present
     * @param beanName optional bean id of the bean
     */
    void onFieldInject(Field field, Object bean, @Nullable String beanName);

    /**
     * Method injection
     *
     * @param method   the method
     * @param bean     the bean instance where the method is present
     * @param beanName optional bean id of the bean
     */
    void onMethodInject(Method method, Object bean, @Nullable String beanName);

}
