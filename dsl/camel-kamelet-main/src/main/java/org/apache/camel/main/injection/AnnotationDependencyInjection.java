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
import java.util.function.Supplier;

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
import org.apache.camel.util.AnnotationHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.StringHelper;

/**
 * To enable camel/spring/quarkus based annotations for dependency injection when loading DSLs.
 */
public final class AnnotationDependencyInjection {

    private static final String SPRING_AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String SPRING_BEAN = "org.springframework.context.annotation.Bean";
    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";
    private static final String SPRING_QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";
    private static final String SPRING_SERVICE = "org.springframework.stereotype.Service";
    private static final String SPRING_VALUE = "org.springframework.beans.factory.annotation.Value";

    private static final String QUARKUS_APPLICATION_SCOPED = "jakarta.enterprise.context.ApplicationScoped";
    private static final String QUARKUS_CONFIG_PROPERTY = "org.eclipse.microprofile.config.inject.ConfigProperty";
    private static final String QUARKUS_INJECT = "jakarta.inject.Inject";
    private static final String QUARKUS_NAMED = "jakarta.inject.Named";
    private static final String QUARKUS_PRODUCES = "jakarta.enterprise.inject.Produces";
    private static final String QUARKUS_SINGLETON = "jakarta.inject.Singleton";

    private final boolean lazyBean;

    public AnnotationDependencyInjection(CamelContext context, boolean lazyBean) {
        this.lazyBean = lazyBean;

        Registry registry = context.getRegistry();
        CamelBeanPostProcessor cbbp = PluginHelper.getBeanPostProcessor(context);
        if (lazyBean) {
            // force lazy beans
            cbbp.setLazyBeanStrategy((ann) -> true);
        }

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
        public void postCompile(
                CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
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
        public void postCompile(
                CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
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

    private class BindToRegistryCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(
                CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {

            BindToRegistry bir = clazz.getAnnotation(BindToRegistry.class);
            Configuration cfg = clazz.getAnnotation(Configuration.class);

            // special for lazy beans which we must create on-demand
            if (instance == null && bir != null && (lazyBean || bir.lazy())) {
                final String beanName = bir.value();
                instance = (Supplier<Object>) () -> {
                    Object answer = camelContext.getInjector().newInstance(clazz);
                    CamelBeanPostProcessor bpp = PluginHelper.getBeanPostProcessor(camelContext);
                    try {
                        bpp.postProcessBeforeInitialization(answer, beanName);
                        bpp.postProcessAfterInitialization(answer, beanName);
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeException(e);
                    }
                    return answer;
                };
                // unbind old bean and register lazy bean
                camelContext.getRegistry().unbind(beanName);
                // use dependency injection factory to perform the task of binding the bean to registry
                Runnable task = PluginHelper.getDependencyInjectionAnnotationFactory(camelContext)
                        .createBindToRegistryFactory(
                                name, instance, clazz, beanName, false, bir.initMethod(), bir.destroyMethod());
                task.run();
            } else {
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
    }

    private class SpringAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(
                CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }
            // @Component and @Service are the same
            String comp = AnnotationHelper.getAnnotationValue(clazz, SPRING_COMPONENT);
            String service = AnnotationHelper.getAnnotationValue(clazz, SPRING_SERVICE);
            if (comp != null || service != null) {
                if (ObjectHelper.isNotEmpty(comp)) {
                    name = comp;
                } else if (ObjectHelper.isNotEmpty(service)) {
                    name = service;
                }
                if (name == null || name.isBlank()) {
                    name = clazz.getSimpleName();
                    // lower case first if using class name
                    name = StringHelper.decapitalize(name);
                }
                bindBean(camelContext, name, instance, instance.getClass(), true);
            }
        }
    }

    private class SpringBeanPostProcessorInjector implements CamelBeanPostProcessorInjector {

        private final CamelContext context;
        private final CamelPostProcessorHelper helper;

        public SpringBeanPostProcessorInjector(CamelContext context) {
            this.context = context;
            this.helper = new CamelPostProcessorHelper(context);
        }

        @Override
        public void onFieldInject(Field field, Object bean, String beanName) {
            boolean autowired = AnnotationHelper.hasAnnotation(field, SPRING_AUTOWIRED);
            if (autowired) {
                String name = null;
                String named = AnnotationHelper.getAnnotationValue(field, SPRING_QUALIFIER);
                if (ObjectHelper.isNotEmpty(named)) {
                    name = named;
                }

                try {
                    ReflectionHelper.setField(field, bean, helper.getInjectionBeanValue(field.getType(), name));
                } catch (NoSuchBeanException e) {
                    Object required = AnnotationHelper.getAnnotationValue(field, SPRING_AUTOWIRED, "required");
                    if (Boolean.TRUE == required) {
                        throw e;
                    }
                    // not required so ignore
                }
            }
            String value = AnnotationHelper.getAnnotationValue(field, SPRING_VALUE);
            if (value != null) {
                ReflectionHelper.setField(
                        field,
                        bean,
                        helper.getInjectionPropertyValue(field.getType(), field.getGenericType(), value, null, null));
            }
        }

        @Override
        public void onMethodInject(Method method, Object bean, String beanName) {
            boolean bi = AnnotationHelper.hasAnnotation(method, SPRING_BEAN);
            if (bi) {
                Object instance;
                if (lazyBean) {
                    instance = (Supplier<Object>)
                            () -> helper.getInjectionBeanMethodValue(context, method, bean, beanName, "Bean");
                } else {
                    instance = helper.getInjectionBeanMethodValue(context, method, bean, beanName, "Bean");
                }
                if (instance != null) {
                    String name = method.getName();
                    String[] names = (String[]) AnnotationHelper.getAnnotationValue(method, SPRING_BEAN, "name");
                    if (names == null) {
                        names = (String[]) AnnotationHelper.getAnnotationValue(method, SPRING_BEAN, "value");
                    }
                    if (names != null && names.length > 0) {
                        name = names[0];
                    }
                    bindBean(context, name, instance, method.getReturnType(), false);
                }
            }
        }
    }

    private class QuarkusAnnotationCompilePostProcessor implements CompilePostProcessor {

        @Override
        public void postCompile(
                CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
                throws Exception {
            if (instance == null) {
                return;
            }
            // @ApplicationScoped and @Singleton are considered the same
            boolean as = AnnotationHelper.hasAnnotation(clazz, QUARKUS_APPLICATION_SCOPED);
            boolean ss = AnnotationHelper.hasAnnotation(clazz, QUARKUS_SINGLETON);
            if (as || ss) {
                String named = AnnotationHelper.getAnnotationValue(clazz, QUARKUS_NAMED);
                if (named != null) {
                    name = named;
                }
                if (name == null || name.isBlank()) {
                    name = clazz.getSimpleName();
                    // lower case first if using class name
                    name = StringHelper.decapitalize(name);
                }
                bindBean(camelContext, name, instance, instance.getClass(), true);
            }
        }
    }

    private class QuarkusBeanPostProcessorInjector implements CamelBeanPostProcessorInjector {

        private final CamelContext context;
        private final CamelPostProcessorHelper helper;

        public QuarkusBeanPostProcessorInjector(CamelContext context) {
            this.context = context;
            this.helper = new CamelPostProcessorHelper(context);
        }

        @Override
        public void onFieldInject(Field field, Object bean, String beanName) {
            boolean inject = AnnotationHelper.hasAnnotation(field, QUARKUS_INJECT);
            if (inject) {
                String name = null;
                String named = AnnotationHelper.getAnnotationValue(field, QUARKUS_NAMED);
                if (named != null) {
                    name = named;
                }

                ReflectionHelper.setField(field, bean, helper.getInjectionBeanValue(field.getType(), name));
            }
            if (AnnotationHelper.hasAnnotation(field, QUARKUS_CONFIG_PROPERTY)) {
                String name = (String) AnnotationHelper.getAnnotationValue(field, QUARKUS_CONFIG_PROPERTY, "name");
                String df =
                        (String) AnnotationHelper.getAnnotationValue(field, QUARKUS_CONFIG_PROPERTY, "defaultValue");
                if ("org.eclipse.microprofile.config.configproperty.unconfigureddvalue".equals(df)) {
                    df = null;
                }
                ReflectionHelper.setField(
                        field,
                        bean,
                        helper.getInjectionPropertyValue(field.getType(), field.getGenericType(), name, df, null));
            }
        }

        @Override
        public void onMethodInject(Method method, Object bean, String beanName) {
            boolean produces = AnnotationHelper.hasAnnotation(method, QUARKUS_PRODUCES);
            boolean inject = AnnotationHelper.hasAnnotation(method, QUARKUS_INJECT);
            boolean bi = AnnotationHelper.hasAnnotation(method, QUARKUS_NAMED);
            if (produces || inject || bi) {
                String an = produces ? "Produces" : "Inject";
                Object instance;
                if (lazyBean) {
                    instance = (Supplier<Object>)
                            () -> helper.getInjectionBeanMethodValue(context, method, bean, beanName, an);
                } else {
                    instance = helper.getInjectionBeanMethodValue(context, method, bean, beanName, an);
                }
                if (instance != null) {
                    String name = method.getName();
                    String named = AnnotationHelper.getAnnotationValue(method, QUARKUS_NAMED);
                    if (ObjectHelper.isNotEmpty(named)) {
                        name = named;
                    }
                    bindBean(context, name, instance, method.getReturnType(), false);
                }
            }
        }
    }

    private static void bindBean(
            CamelContext context, String name, Object instance, Class<?> type, boolean postProcess) {
        // to support hot reloading of beans then we need to enable unbind mode in bean post processor
        Registry registry = context.getRegistry();
        CamelBeanPostProcessor bpp = PluginHelper.getBeanPostProcessor(context);
        bpp.setUnbindEnabled(true);
        try {
            // re-bind the bean to the registry
            registry.unbind(name);
            if (instance instanceof Supplier sup) {
                registry.bind(name, type, (Supplier<Object>) sup);
            } else {
                registry.bind(name, type, instance);
            }
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
