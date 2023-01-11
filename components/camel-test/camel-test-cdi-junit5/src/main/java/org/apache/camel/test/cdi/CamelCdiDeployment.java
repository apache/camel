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
package org.apache.camel.test.cdi;

import jakarta.enterprise.inject.spi.BeanManager;

import org.apache.camel.cdi.CdiCamelExtension;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.extension.ExtensionContext;

final class CamelCdiDeployment implements ExtensionContext.Store.CloseableResource {

    private final BeanManager beanManager;
    private final WeldContainer container;

    CamelCdiDeployment(Class<?> test) {
        this.container = createWeldContainer(test);
        this.beanManager = container.getBeanManager();
    }

    BeanManager beanManager() {
        return beanManager;
    }

    private static WeldContainer createWeldContainer(Class<?> test) {
        final Weld weld = new Weld()
                .containerId(String.format("camel-context-cdi-%s", test.getCanonicalName()))
                .property(ConfigurationKey.RELAXED_CONSTRUCTION.get(), true)
                .property(Weld.SHUTDOWN_HOOK_SYSTEM_PROPERTY, false)
                .enableDiscovery()
                .beanClasses(test.getDeclaredClasses())
                .addBeanClass(test)
                .addExtension(new CdiCamelExtension());

        // Apply deployment customization provided by the @Beans annotation
        // if present on the test class
        Beans beans = null;
        if (test.isAnnotationPresent(Beans.class)) {
            beans = test.getAnnotation(Beans.class);
            for (Class<?> alternative : beans.alternatives()) {
                // It is not necessary to add the alternative class with WELD-2218
                // anymore, though it's kept for previous versions
                weld.addBeanClass(alternative)
                        .addAlternative(alternative);
            }
            for (Class<?> clazz : beans.classes()) {
                weld.addBeanClass(clazz);
            }
            weld.addPackages(false, beans.packages());
        }
        weld.addExtension(new CamelCdiTestExtension(beans));
        return weld.initialize();
    }

    @Override
    public void close() {
        container.shutdown();
    }
}
