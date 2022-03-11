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
package org.apache.camel.main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessorInjector;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

public final class SpringAnnotationSupport {

    private SpringAnnotationSupport() {
    }

    public static void registerSpringSupport(CamelContext context) {
        context.getRegistry().bind("SpringAnnotationCompilePostProcessor", new SpringAnnotationCompilePostProcessor());
        context.adapt(ExtendedCamelContext.class).getBeanPostProcessor()
                .addCamelBeanPostProjectInjector(new SpringBeanPostProcessorInjector(context));
    }

    private static class SpringAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, Object instance) throws Exception {
            // @Component and @Service are the same
            Component comp = clazz.getAnnotation(Component.class);
            Service service = clazz.getAnnotation(Service.class);
            if (comp != null || service != null) {
                CamelBeanPostProcessor bpp = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
                if (comp != null && ObjectHelper.isNotEmpty(comp.value())) {
                    name = comp.value();
                } else if (service != null && ObjectHelper.isNotEmpty(service.value())) {
                    name = service.value();
                }
                // to support hot reloading of beans then we need to enable unbind mode in bean post processor
                bpp.setUnbindEnabled(true);
                try {
                    // re-bind the bean to the registry
                    camelContext.getRegistry().unbind(name);
                    camelContext.getRegistry().bind(name, instance);
                    // this class is a bean service which needs to be post processed
                    bpp.postProcessBeforeInitialization(instance, name);
                    bpp.postProcessAfterInitialization(instance, name);
                } finally {
                    bpp.setUnbindEnabled(false);
                }
            }
        }
    }

    private static class SpringBeanPostProcessorInjector implements CamelBeanPostProcessorInjector {

        private final CamelContext context;
        private final CamelPostProcessorHelper helper;

        public SpringBeanPostProcessorInjector(CamelContext context) {
            this.context = context;
            this.helper = new CamelPostProcessorHelper(context);
        }

        @Override
        public void onFieldInject(Field field, Object bean, String beanName) {
            Autowired autowired = field.getAnnotation(Autowired.class);
            if (autowired != null) {
                String name = null;
                Qualifier qualifier = field.getAnnotation(Qualifier.class);
                if (qualifier != null) {
                    name = qualifier.value();
                }

                try {
                    ReflectionHelper.setField(field, bean,
                            helper.getInjectionBeanValue(field.getType(), name));
                } catch (NoSuchBeanException e) {
                    if (autowired.required()) {
                        throw e;
                    }
                    // not required so ignore
                }
            }
            Value value = field.getAnnotation(Value.class);
            if (value != null) {
                ReflectionHelper.setField(field, bean,
                        helper.getInjectionPropertyValue(field.getType(), value.value(), null, null, bean, beanName));
            }
        }

        @Override
        public void onMethodInject(Method method, Object bean, String beanName) {
            Bean bi = method.getAnnotation(Bean.class);
            if (bi != null) {
                Object instance = helper.getInjectionBeanMethodValue(context, method, bean, beanName);
                if (instance != null) {
                    String name = method.getName();
                    if (bi.name() != null && bi.name().length > 0) {
                        name = bi.name()[0];
                    }
                    // to support hot reloading of beans then we need to enable unbind mode in bean post processor
                    CamelBeanPostProcessor bpp = context.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
                    bpp.setUnbindEnabled(true);
                    try {
                        // re-bind the bean to the registry
                        context.getRegistry().unbind(name);
                        context.getRegistry().bind(name, instance);
                    } finally {
                        bpp.setUnbindEnabled(false);
                    }
                }
            }
        }
    }
}
