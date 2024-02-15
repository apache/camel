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
package org.apache.camel.main.injection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Configuration;
import org.apache.camel.Converter;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.impl.engine.CamelPostProcessorHelper;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelBeanPostProcessorInjector;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * To enable camel/spring/quarkus based annotations for dependency injection when loading DSLs.
 */
public final class AnnotationDependencyInjection {

    private AnnotationDependencyInjection() {
    }

    public static void initAnnotationBasedDependencyInjection(CamelContext context) {
        Registry registry = context.getRegistry();
        CamelBeanPostProcessor cbbp = PluginHelper.getBeanPostProcessor(context);

        // camel / common
        registry.bind("CamelTypeConverterCompilePostProcessor", new TypeConverterCompilePostProcessor());
        registry.bind("CamelEventNotifierCompilePostProcessor", new EventNotifierCompilePostProcessor());
        registry.bind("CamelBindToRegistryCompilePostProcessor", new BindToRegistryCompilePostProcessor());
        // spring
        registry.bind("SpringAnnotationCompilePostProcessor", new SpringAnnotationCompilePostProcessor());
        cbbp.addCamelBeanPostProjectInjector(new SpringBeanPostProcessorInjector(context));
        // quarkus
        registry.bind("QuarkusAnnotationCompilePostProcessor", new QuarkusAnnotationCompilePostProcessor());
        cbbp.addCamelBeanPostProjectInjector(new QuarkusBeanPostProcessorInjector(context));
    }

    private static class TypeConverterCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (clazz.isAnnotationPresent(Converter.class)) {
                TypeConverterRegistry tcr = camelContext.getTypeConverterRegistry();
                TypeConverterExists exists = tcr.getTypeConverterExists();
                LoggingLevel level = tcr.getTypeConverterExistsLoggingLevel();
                // force type converter to override as we could be re-loading
                tcr.setTypeConverterExists(TypeConverterExists.Override);
                tcr.setTypeConverterExistsLoggingLevel(LoggingLevel.OFF);
                try {
                    tcr.addTypeConverters(clazz);
                } finally {
                    tcr.setTypeConverterExists(exists);
                    tcr.setTypeConverterExistsLoggingLevel(level);
                }
            }
        }

    }

    private static class EventNotifierCompilePostProcessor implements CompilePostProcessor {

        private final Map<String, EventNotifier> notifiers = new HashMap<>();

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }

            if (instance instanceof EventNotifier) {
                ManagementStrategy ms = camelContext.getManagementStrategy();
                if (ms != null) {
                    notifiers.compute(name, (key, old) -> {
                        // remove previous instance
                        if (old != null) {
                            ms.removeEventNotifier(old);
                        }
                        // and new notifier
                        EventNotifier en = (EventNotifier) instance;
                        ms.addEventNotifier(en);
                        return en;
                    });
                }
            }
        }
    }

    private static class BindToRegistryCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }

            BindToRegistry bir = instance.getClass().getAnnotation(BindToRegistry.class);
            Configuration cfg = instance.getClass().getAnnotation(Configuration.class);
            if (bir != null || cfg != null || instance instanceof CamelConfiguration) {
                CamelBeanPostProcessor bpp = PluginHelper.getBeanPostProcessor(camelContext);
                if (bir != null && ObjectHelper.isNotEmpty(bir.value())) {
                    name = bir.value();
                } else if (cfg != null && ObjectHelper.isNotEmpty(cfg.value())) {
                    name = cfg.value();
                }
                // to support hot reloading of beans then we need to enable unbind mode in bean post processor
                bpp.setUnbindEnabled(true);
                try {
                    // this class uses camels own annotations so the bind to registry happens
                    // automatic by the bean post processor
                    bpp.postProcessBeforeInitialization(instance, name);
                    bpp.postProcessAfterInitialization(instance, name);
                } finally {
                    bpp.setUnbindEnabled(false);
                }
                if (instance instanceof CamelConfiguration) {
                    ((CamelConfiguration) instance).configure(camelContext);
                }
            }
        }

    }

    private static class SpringAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }
            // @Component and @Service are the same
            Component comp = clazz.getAnnotation(Component.class);
            Service service = clazz.getAnnotation(Service.class);
            if (comp != null || service != null) {
                if (comp != null && ObjectHelper.isNotEmpty(comp.value())) {
                    name = comp.value();
                } else if (service != null && ObjectHelper.isNotEmpty(service.value())) {
                    name = service.value();
                }
                bindBean(camelContext, name, instance, true);
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
                    if (bi.name().length > 0) {
                        name = bi.name()[0];
                    }
                    bindBean(context, name, instance, false);
                }
            }
        }
    }

    private static class QuarkusAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }
            // @ApplicationScoped and @Singleton are considered the same
            ApplicationScoped as = clazz.getAnnotation(ApplicationScoped.class);
            Singleton ss = clazz.getAnnotation(Singleton.class);
            if (as != null || ss != null) {
                Named named = clazz.getAnnotation(Named.class);
                if (named != null) {
                    name = named.value();
                }
                bindBean(camelContext, name, instance, true);
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
                    bindBean(context, name, instance, false);
                }
            }
        }
    }

    private static void bindBean(CamelContext context, String name, Object instance, boolean postProcess) {
        // to support hot reloading of beans then we need to enable unbind mode in bean post processor
        Registry registry = context.getRegistry();
        CamelBeanPostProcessor bpp = PluginHelper.getBeanPostProcessor(context);
        bpp.setUnbindEnabled(true);
        try {
            // re-bind the bean to the registry
            registry.unbind(name);
            registry.bind(name, instance);
            if (postProcess) {
                bpp.postProcessBeforeInitialization(instance, name);
                bpp.postProcessAfterInitialization(instance, name);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        } finally {
            bpp.setUnbindEnabled(false);
        }
    }

}
