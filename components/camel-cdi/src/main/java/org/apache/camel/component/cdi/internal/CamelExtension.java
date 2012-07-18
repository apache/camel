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
package org.apache.camel.component.cdi.internal;

import java.lang.reflect.Method;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.cdi.CdiCamelContext;
import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;

/**
 * Set of camel specific hooks for CDI.
 */
public class CamelExtension implements Extension {

    /**
     * Context instance.
     */
    private CamelContext camelContext;

    /**
     * Process camel context aware bean definitions.
     * 
     * @param process Annotated type.
     * @throws Exception In case of exceptions.
     */
    protected void contextAwareness(@Observes ProcessAnnotatedType<CamelContextAware> process) throws Exception {
        AnnotatedType<CamelContextAware> annotatedType = process.getAnnotatedType();
        Class<CamelContextAware> javaClass = annotatedType.getJavaClass();
        if (CamelContextAware.class.isAssignableFrom(javaClass)) {
            Method method = javaClass.getMethod("setCamelContext", CamelContext.class);
            AnnotatedTypeBuilder<CamelContextAware> builder = new AnnotatedTypeBuilder<CamelContextAware>()
                .readFromType(javaClass)
                .addToMethod(method, new InjectLiteral());
            process.setAnnotatedType(builder.create());
        }
    }

    /**
     * Disable creation of default CamelContext bean and rely on context created
     * and managed by extension.
     *
     * @param process Annotated type.
     */
    protected void disableDefaultContext(@Observes ProcessAnnotatedType<CamelContext> process) {
        process.veto();
    }

    /**
     * Registers managed camel bean.
     * 
     * @param abd After bean discovery event.
     * @param manager Bean manager.
     */
    protected void registerManagedCamelContext(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        abd.addBean(new CamelContextBean(manager.createInjectionTarget(manager.createAnnotatedType(CdiCamelContext.class))));
    }

    /**
     * Start up camel context.
     * 
     * @param adv After deployment validation event.
     * @throws Exception In case of failures.
     */
    protected void validate(@Observes AfterDeploymentValidation adv) throws Exception {
        camelContext = BeanProvider.getContextualReference(CamelContext.class);
        camelContext.start();
    }

    /**
     * Shutdown camel context.
     * 
     * @param bsd Shutdown event.
     * @throws Exception In case of failures.
     */
    protected void shutdown(@Observes BeforeShutdown bsd) throws Exception {
        camelContext.stop();
    }

}
