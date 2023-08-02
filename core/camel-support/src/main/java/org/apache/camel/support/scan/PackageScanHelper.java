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
package org.apache.camel.support.scan;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.PluginHelper;

import static org.apache.camel.util.ObjectHelper.isEmpty;

/**
 * Helper for Camel package scanning.
 */
public class PackageScanHelper {

    private PackageScanHelper() {
    }

    /**
     * Scans the given Java packages for custom beans annotated with {@link BindToRegistry} and create new instances of
     * these class and performs Camel dependency injection via {@link org.apache.camel.spi.CamelBeanPostProcessor}.
     *
     * @param camelContext the camel context
     * @param packages     the Java packages to scan
     */
    public static void registerBeans(CamelContext camelContext, Set<String> packages) {
        if (packages != null && !packages.isEmpty()) {
            Registry registry = camelContext.getRegistry();
            if (registry != null) {
                PackageScanClassResolver scanner
                        = camelContext.getCamelContextExtension().getContextPlugin(PackageScanClassResolver.class);
                Injector injector = camelContext.getInjector();
                if (scanner != null && injector != null) {
                    Map<Class<?>, Object> created = new HashMap<>();
                    for (String pkg : packages) {
                        Set<Class<?>> classes = scanner.findAnnotated(BindToRegistry.class, pkg);
                        for (Class<?> c : classes) {
                            // phase-1: create empty bean instance without any bean post-processing
                            Object b = injector.newInstance(c, false);
                            if (b != null) {
                                created.put(c, b);
                            }
                        }
                        for (Class<?> c : created.keySet()) {
                            // phase-2: discover any created beans has @BindToRegistry to register them eager
                            BindToRegistry ann = c.getAnnotation(BindToRegistry.class);
                            if (ann != null) {
                                String name = ann.value();
                                if (isEmpty(name)) {
                                    name = c.getSimpleName();
                                }
                                Object bean = created.get(c);
                                String beanName = c.getName();
                                // - bind to registry if @org.apache.camel.BindToRegistry is present
                                // use dependency injection factory to perform the task of binding the bean to registry
                                Runnable task = PluginHelper.getDependencyInjectionAnnotationFactory(camelContext)
                                        .createBindToRegistryFactory(name, bean, beanName, false);
                                task.run();
                            }
                        }
                        for (Class<?> c : created.keySet()) {
                            // phase-3: now we can do bean post-processing on the created beans
                            Object bean = created.get(c);
                            String beanName = c.getName();
                            try {
                                // - call org.apache.camel.spi.CamelBeanPostProcessor.postProcessBeforeInitialization
                                // - call org.apache.camel.spi.CamelBeanPostProcessor.postProcessAfterInitialization
                                PluginHelper.getBeanPostProcessor(camelContext).postProcessBeforeInitialization(bean,
                                        beanName);
                                PluginHelper.getBeanPostProcessor(camelContext).postProcessAfterInitialization(bean,
                                        beanName);
                            } catch (Exception e) {
                                throw new RuntimeCamelException("Error post-processing bean: " + beanName, e);
                            }
                        }
                    }
                }
            }
        }
    }
}
