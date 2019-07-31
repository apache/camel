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

import org.apache.camel.cdi.CdiCamelExtension;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

final class CamelCdiDeployment implements TestRule {

    private final CamelCdiContext context;

    private final Weld weld;

    CamelCdiDeployment(TestClass test, CamelCdiContext context) {
        this.context = context;

        weld = new Weld()
            // TODO: check parallel execution
            .containerId("camel-context-cdi")
            .property(ConfigurationKey.RELAXED_CONSTRUCTION.get(), true)
            .property(Weld.SHUTDOWN_HOOK_SYSTEM_PROPERTY, false)
            .enableDiscovery()
            .beanClasses(test.getJavaClass().getDeclaredClasses())
            .addBeanClass(test.getJavaClass())
            .addExtension(new CdiCamelExtension());

        // Apply deployment customization provided by the @Beans annotation
        // if present on the test class
        if (test.getJavaClass().isAnnotationPresent(Beans.class)) {
            Beans beans = test.getJavaClass().getAnnotation(Beans.class);
            weld.addExtension(new CamelCdiTestExtension(beans));
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
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                WeldContainer container = weld.initialize();
                context.setBeanManager(container.getBeanManager());
                try {
                    base.evaluate();
                } finally {
                    container.shutdown();
                    context.unsetBeanManager();
                }
            }
        };
    }
}
