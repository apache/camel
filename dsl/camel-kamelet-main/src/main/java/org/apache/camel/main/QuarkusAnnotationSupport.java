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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessorInjector;
import org.apache.camel.util.ReflectionHelper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * To support quarkus based annotations such as @ApplicationScoped, @Singleton, @Inject, @Produces, @ConfigProperty
 */
public final class QuarkusAnnotationSupport {

    private QuarkusAnnotationSupport() {
    }

    public static void registerSpringSupport(CamelContext context) {
        context.getRegistry().bind("QuarkusAnnotationCompilePostProcessor", new QuarkusAnnotationCompilePostProcessor());
        context.adapt(ExtendedCamelContext.class).getBeanPostProcessor()
                .addCamelBeanPostProjectInjector(new QuarkusBeanPostProcessorInjector(context));
    }

    private static class QuarkusAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, Object instance) throws Exception {
            // @ApplicationScoped and @Singleton are considered the same
            ApplicationScoped as = clazz.getAnnotation(ApplicationScoped.class);
            Singleton ss = clazz.getAnnotation(Singleton.class);
            if (as != null || ss != null) {
                Named named = clazz.getAnnotation(Named.class);
                if (named != null) {
                    name = named.value();
                }
                CamelBeanPostProcessor bpp = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
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

    private static class QuarkusBeanPostProcessorInjector implements CamelBeanPostProcessorInjector {

        private final CamelContext context;
        private final CamelPostProcessorHelper helper;

        public QuarkusBeanPostProcessorInjector(CamelContext context) {
            this.context = context;
            this.helper = new CamelPostProcessorHelper(context);
        }

        @Override
        public void onFieldInject(Field field, Object bean, String beanName) {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject != null) {
                String name = null;
                Named named = field.getAnnotation(Named.class);
                if (named != null) {
                    name = named.value();
                }

                ReflectionHelper.setField(field, bean,
                        helper.getInjectionBeanValue(field.getType(), name));
            }
            ConfigProperty cp = field.getAnnotation(ConfigProperty.class);
            if (cp != null) {
                ReflectionHelper.setField(field, bean,
                        helper.getInjectionPropertyValue(field.getType(), cp.name(), cp.defaultValue(), null, bean, beanName));
            }
        }

        @Override
        public void onMethodInject(Method method, Object bean, String beanName) {
            Produces produces = method.getAnnotation(Produces.class);
            Named bi = method.getAnnotation(Named.class);
            if (produces != null || bi != null) {
                Object instance = helper.getInjectionBeanMethodValue(context, method, bean, beanName);
                if (instance != null) {
                    String name = method.getName();
                    if (bi != null && !bi.value().isBlank()) {
                        name = bi.value();
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
