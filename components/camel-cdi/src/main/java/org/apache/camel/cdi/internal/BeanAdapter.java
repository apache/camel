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
package org.apache.camel.cdi.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;

/**
 * Contains the bean and the consume methods
 */
public class BeanAdapter {
    
    private final List<Method> consumeMethods = new ArrayList<Method>();
    private final List<Method> produceMethods = new ArrayList<Method>();
    private final List<Method> endpointMethods = new ArrayList<Method>();
    private final List<Field> produceFields = new ArrayList<Field>();
    private final List<Field> endpointFields = new ArrayList<Field>();
    private final ContextName startup;

    public BeanAdapter(ContextName startup) {
        this.startup = startup;
    }

    /**
     * Returns true if this adapter is empty (i.e. has no custom adapter code)
     */
    public boolean isEmpty() {
        return consumeMethods.isEmpty() && produceMethods.isEmpty() && produceFields.isEmpty() 
            && endpointMethods.isEmpty() && endpointFields.isEmpty();
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

    public void addEndpointField(Field field) {
        endpointFields.add(field);
    }

    public void addEndpointMethod(Method method) {
        endpointMethods.add(method);
    }

    /**
     * Perform injections
     */
    public void inject(CamelExtension camelExtension, Object reference, String beanName) {
        for (Method method : consumeMethods) {
            Consume annotation = method.getAnnotation(Consume.class);
            if (annotation != null) {
                String contextName = CamelExtension.getCamelContextName(annotation.context(), startup);
                DefaultCamelBeanPostProcessor postProcessor = camelExtension.getPostProcessor(contextName, null);
                if (postProcessor != null) {
                    postProcessor.getPostProcessorHelper().consumerInjection(method, reference, beanName);
                }
            }
        }
        for (Method method : produceMethods) {
            Produce annotation = method.getAnnotation(Produce.class);
            if (annotation != null) {
                String contextName = CamelExtension.getCamelContextName(annotation.context(), startup);
                DefaultCamelBeanPostProcessor postProcessor = camelExtension.getPostProcessor(contextName, null);
                if (postProcessor != null && postProcessor.getPostProcessorHelper().matchContext(contextName)) {
                    postProcessor.setterInjection(method, reference, beanName, annotation.uri(), annotation.ref(),
                            annotation.property());
                }
            }
        }
        for (Method method : endpointMethods) {
            EndpointInject annotation = method.getAnnotation(EndpointInject.class);
            if (annotation != null) {
                String contextName = CamelExtension.getCamelContextName(annotation.context(), startup);
                DefaultCamelBeanPostProcessor postProcessor = camelExtension.getPostProcessor(contextName, null);
                if (postProcessor != null && postProcessor.getPostProcessorHelper().matchContext(contextName)) {
                    postProcessor.setterInjection(method, reference, beanName, annotation.uri(), annotation.ref(),
                            annotation.property());

                }
            }
        }
        for (Field field : produceFields) {
            Produce annotation = field.getAnnotation(Produce.class);
            if (annotation != null) {
                String contextName = CamelExtension.getCamelContextName(annotation.context(), startup);
                DefaultCamelBeanPostProcessor postProcessor = camelExtension.getPostProcessor(contextName, null);
                if (postProcessor != null && postProcessor.getPostProcessorHelper().matchContext(contextName)) {
                    postProcessor.injectField(field, annotation.uri(), annotation.ref(),
                            annotation.property(), reference, beanName);
                }
            }
        }
        for (Field field : endpointFields) {
            EndpointInject annotation = field.getAnnotation(EndpointInject.class);
            String contextName = CamelExtension.getCamelContextName(annotation.context(), startup);
            DefaultCamelBeanPostProcessor postProcessor = camelExtension.getPostProcessor(contextName, null);
            if (postProcessor != null && postProcessor.getPostProcessorHelper().matchContext(contextName)) {
                postProcessor.injectField(field, annotation.uri(), annotation.ref(),
                        annotation.property(), reference, beanName);
            }
        }
    }

}
