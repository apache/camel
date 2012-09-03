/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cdi.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.inject.spi.Bean;

import org.apache.camel.Produce;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;

/**
 * Contains the bean and the consume methods
 */
public class BeanAdapter {
    private final Bean<?> bean;
    private final List<Method> consumeMethods = new ArrayList<Method>();
    private final List<Method> produceMethods = new ArrayList<Method>();
    private final List<Field> produceFields = new ArrayList<Field>();

    public BeanAdapter(Bean<?> bean) {
        this.bean = bean;
    }

    public Bean<?> getBean() {
        return bean;
    }

    public List<Method> getConsumeMethods() {
        return consumeMethods;
    }

    public List<Method> getProduceMethods() {
        return produceMethods;
    }

    public List<Field> getProduceFields() {
        return produceFields;
    }

    public void addConsumeMethod(Method method) {
        consumeMethods.add(method);
    }

    public void addProduceMethod(Method method) {
        produceMethods.add(method);
    }

    public void addProduceField(Field field) {
        produceFields.add(field);
    }

    /**
     * Perform processing of the various @Consume, @Produce methods on the given bean reference
     */
    public void initialiseBean(DefaultCamelBeanPostProcessor postProcessor, Object reference,
                               String beanName) {
        CamelPostProcessorHelper postProcessorHelper = postProcessor.getPostProcessorHelper();
        for (Method method : consumeMethods) {
            postProcessorHelper.consumerInjection(method, reference, beanName);
        }
        for (Method method : produceMethods) {
            Produce produce = method.getAnnotation(Produce.class);
            if (produce != null && postProcessorHelper.matchContext(produce.context())) {
                postProcessor.setterInjection(method, bean, beanName, produce.uri(), produce.ref(),
                        produce.property());

            }
        }
        for (Field field : produceFields) {
            Produce produce = field.getAnnotation(Produce.class);
            if (produce != null && postProcessorHelper.matchContext(produce.context())) {
                postProcessor.injectField(field, produce.uri(), produce.ref(),
                        produce.property(), reference, beanName);
            }
        }
    }

}
