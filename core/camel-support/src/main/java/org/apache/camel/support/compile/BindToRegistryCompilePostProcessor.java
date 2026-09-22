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
package org.apache.camel.support.compile;

import java.util.function.Supplier;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Configuration;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Binds a compiled class annotated with {@link BindToRegistry} or {@link Configuration} (or that is a
 * {@link CamelConfiguration}) to the registry, using the {@link CamelBeanPostProcessor} so the Camel dependency
 * injection annotations on the class are processed as well. As the class may be compiled again (live reload) the
 * previous bean is unbound first.
 */
public class BindToRegistryCompilePostProcessor implements CompilePostProcessor {

    private final boolean lazyBean;

    public BindToRegistryCompilePostProcessor() {
        this(false);
    }

    /**
     * @param lazyBean whether to bind every {@link BindToRegistry} bean lazily (created on first use), as if
     *                 {@link BindToRegistry#lazy()} was set
     */
    public BindToRegistryCompilePostProcessor(boolean lazyBean) {
        this.lazyBean = lazyBean;
    }

    @Override
    public void postCompile(CamelContext camelContext, String name, Class<?> clazz, byte[] byteCode, Object instance)
            throws Exception {

        BindToRegistry bir = clazz.getAnnotation(BindToRegistry.class);
        Configuration cfg = clazz.getAnnotation(Configuration.class);

        // special for lazy beans which we must create on-demand
        if (instance == null && bir != null && (lazyBean || bir.lazy())) {
            // the bean id is the annotation value, else the simple class name (as for an eager bean)
            final String id = ObjectHelper.isNotEmpty(bir.value()) ? bir.value() : clazz.getSimpleName();
            final String beanName = name;
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
            camelContext.getRegistry().unbind(id);
            // use dependency injection factory to perform the task of binding the bean to registry
            Runnable task = PluginHelper.getDependencyInjectionAnnotationFactory(camelContext)
                    .createBindToRegistryFactory(id, instance, clazz, beanName, false, bir.initMethod(),
                            bir.destroyMethod());
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
                if (instance instanceof CamelConfiguration cc) {
                    cc.configure(camelContext);
                }
            }
        }
    }
}
