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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consume;
import org.apache.camel.Produce;
import org.apache.camel.component.cdi.CdiCamelContext;
import org.apache.camel.impl.CamelPostProcessorHelper;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
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

    private Map<Bean<?>, BeanAdapter> beanAdapters = new HashMap<Bean<?>, BeanAdapter>();

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
     * @param abd     After bean discovery event.
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
        getCamelContext().start();
    }

    /**
     * Shutdown camel context.
     *
     * @param bsd Shutdown event.
     * @throws Exception In case of failures.
     */
    protected void shutdown(@Observes BeforeShutdown bsd) throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    public void detectConsumeBeans(@Observes ProcessBean<?> event) {
        final Bean<?> bean = event.getBean();
        ReflectionHelper.doWithMethods(bean.getBeanClass(), new ReflectionHelper.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                Consume consume = method.getAnnotation(Consume.class);
                if (consume != null) {
                    BeanAdapter beanAdapter = getBeanAdapter(bean);
                    beanAdapter.addConsumeMethod(method);
                }
                Produce produce = method.getAnnotation(Produce.class);
                if (produce != null) {
                    BeanAdapter beanAdapter = getBeanAdapter(bean);
                    beanAdapter.addProduceMethod(method);
                }
            }
        });
        ReflectionHelper.doWithFields(bean.getBeanClass(), new ReflectionHelper.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                Produce produce = field.getAnnotation(Produce.class);
                if (produce != null && !injectAnnotatedField(field)) {
                    BeanAdapter beanAdapter = getBeanAdapter(bean);
                    beanAdapter.addProduceField(field);
                }
            }
        });
    }

    /**
     * Returns true if this field is annotated with @Inject
     */
    protected static boolean injectAnnotatedField(Field field) {
        return field.getAnnotation(Inject.class) != null;
    }

    public void initializeBeans(@Observes AfterDeploymentValidation event, BeanManager beanManager) {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        DefaultCamelBeanPostProcessor postProcessor = new DefaultCamelBeanPostProcessor(getCamelContext());
        Collection<BeanAdapter> adapters = beanAdapters.values();
        for (BeanAdapter adapter : adapters) {
            Bean<?> bean = adapter.getBean();
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);

            Object reference = beanManager.getReference(bean, Object.class, creationalContext);
            String beanName = bean.getName();

            adapter.initialiseBean(postProcessor, reference, beanName);
        }
    }

    protected BeanAdapter getBeanAdapter(Bean<?> bean) {
        BeanAdapter beanAdapter = beanAdapters.get(bean);
        if (beanAdapter == null) {
            beanAdapter = new BeanAdapter(bean);
            beanAdapters.put(bean, beanAdapter);
        }
        return beanAdapter;
    }

    public CamelContext getCamelContext() {
        if (camelContext == null) {
            camelContext = BeanProvider.getContextualReference(CamelContext.class);
        }
        return camelContext;
    }
}
