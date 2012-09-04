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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CamelStartup;
import org.apache.camel.component.cdi.CdiCamelContext;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;
import org.apache.camel.util.ReflectionHelper;
import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;

/**
 * Set of camel specific hooks for CDI.
 */
public class CamelExtension implements Extension {

    private Map<Bean<?>, BeanAdapter> eagerBeans = new HashMap<Bean<?>, BeanAdapter>();
    private Map<String, List<Bean<?>>> namedCamelContexts = new HashMap<String, List<Bean<?>>>();
    private List<CamelContextBean> camelContextBeans = new ArrayList<CamelContextBean>();
    private Map<String, CamelContext> camelContexts = new HashMap<String, CamelContext>();

    public CamelExtension() {
    }

    /**
     * Process camel context aware bean definitions.
     *
     * @param process Annotated type.
     * @throws Exception In case of exceptions.
     */
    protected void contextAwareness(@Observes ProcessAnnotatedType<CamelContextAware> process)
        throws Exception {
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
        // lets ensure we have at least one camel context
        if (namedCamelContexts.isEmpty()) {
            abd.addBean(new CamelContextBean(manager));
        } else {
            Set<Map.Entry<String, List<Bean<?>>>> entries = namedCamelContexts.entrySet();
            for (Map.Entry<String, List<Bean<?>>> entry : entries) {
                String name = entry.getKey();
                List<Bean<?>> beans = entry.getValue();
                CamelContextBean camelContextBean = new CamelContextBean(manager, "CamelContext:" + name, name, beans);
                camelContextBeans.add(camelContextBean);
                abd.addBean(camelContextBean);
            }
        }
    }

    /**
     * Lets detect all beans annotated with @Consume and
     * beans of type {@link RouteBuilder} which are annotated with {@link org.apache.camel.cdi.CamelStartup}
     * so they can be auto-registered
     */
    public void detectConsumeBeans(@Observes ProcessBean<?> event) {
        final Bean<?> bean = event.getBean();
        Class<?> beanClass = bean.getBeanClass();
        ReflectionHelper.doWithMethods(beanClass, new ReflectionHelper.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                Consume consume = method.getAnnotation(Consume.class);
                if (consume != null) {
                    eagerlyCreate(bean);
                }
            }
        });

        // detect all RouteBuilder instances
        if (RouteBuilder.class.isAssignableFrom(beanClass)) {
            CamelStartup annotation = beanClass.getAnnotation(CamelStartup.class);
            if (annotation != null) {
                String contextName = annotation.contextName();
                List<Bean<?>> beans = namedCamelContexts.get(contextName);
                if (beans == null) {
                    beans = new ArrayList<Bean<?>>();
                    namedCamelContexts.put(contextName, beans);
                }
                beans.add(bean);
            }
        }
    }

    /**
     * Lets force the CDI container to create all beans annotated with @Consume so that the consumer becomes active
     */
    public void startConsumeBeans(@Observes AfterDeploymentValidation event, BeanManager beanManager) throws Exception {
        if (camelContextBeans.isEmpty()) {
            CamelContext camelContext = BeanProvider.getContextualReference(CamelContext.class);
            camelContexts.put("", camelContext);
        }
        for (CamelContextBean camelContextBean : camelContextBeans) {
            CdiCamelContext context = camelContextBean.configure();
            camelContexts.put(camelContextBean.getCamelContextName(), context);
        }

        Set<Map.Entry<Bean<?>, BeanAdapter>> entries = eagerBeans.entrySet();
        for (Map.Entry<Bean<?>, BeanAdapter> entry : entries) {
            Bean<?> bean = entry.getKey();
            BeanAdapter adapter = entry.getValue();
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);

            // force lazy creation
            beanManager.getReference(bean, Object.class, creationalContext);
        }
    }


    /**
     * Lets perform injection of all beans which use Camel annotations
     */
    @SuppressWarnings("unchecked")
    public void onInjectionTarget(@Observes ProcessInjectionTarget event) {
        final InjectionTarget injectionTarget = event.getInjectionTarget();
        final Class beanClass = event.getAnnotatedType().getJavaClass();
        // TODO this is a bit of a hack - what should the bean name be?
        final String beanName = event.getInjectionTarget().toString();
        final BeanAdapter adapter = createBeanAdapter(beanClass);
        if (!adapter.isEmpty()) {
            DelegateInjectionTarget newTarget = new DelegateInjectionTarget(injectionTarget) {

                @Override
                public void postConstruct(Object instance) {
                    super.postConstruct(instance);

                    // now lets do the post instruct to inject our Camel injections
                    adapter.inject(CamelExtension.this, instance, beanName);
                }
            };
            event.setInjectionTarget(newTarget);
        }
    }

    /**
     * Perform injection on an existing bean such as a test case which is created directly by a testing framework.
     *
     * This is because BeanProvider.injectFields() does not invoke the onInjectionTarget() method so the injection
     * of @Produce / @EndpointInject and processing of the @Consume annotations are not performed.
     */
    public void inject(Object bean) {
        final BeanAdapter adapter = createBeanAdapter(bean.getClass());
        if (!adapter.isEmpty()) {
            // TODO this is a bit of a hack - what should the bean name be?
            final String beanName = bean.toString();
            adapter.inject(this, bean, beanName);
        }
    }

    private BeanAdapter createBeanAdapter(Class beanClass) {
        final BeanAdapter adapter = new BeanAdapter();
        ReflectionHelper.doWithFields(beanClass, new ReflectionHelper.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                Produce produce = field.getAnnotation(Produce.class);
                if (produce != null && !injectAnnotatedField(field)) {
                    adapter.addProduceField(field);
                }
                EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
                if (endpointInject != null) {
                    adapter.addEndpointField(field);
                }
            }
        });
        ReflectionHelper.doWithMethods(beanClass, new ReflectionHelper.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                Consume consume = method.getAnnotation(Consume.class);
                if (consume != null) {
                    adapter.addConsumeMethod(method);
                }
                Produce produce = method.getAnnotation(Produce.class);
                if (produce != null) {
                    adapter.addProduceMethod(method);
                }
                EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
                if (endpointInject != null) {
                    adapter.addEndpointMethod(method);
                }
            }
        });
        return adapter;
    }

    protected DefaultCamelBeanPostProcessor getPostProcessor(String context) {
        CamelContext camelContext = camelContexts.get(context);
        if (camelContext != null) {
            return new DefaultCamelBeanPostProcessor(camelContext);
        } else {
            throw new IllegalArgumentException("No such CamelContext '" + context + "' available!");
        }
    }

    protected BeanAdapter eagerlyCreate(Bean<?> bean) {
        BeanAdapter beanAdapter = eagerBeans.get(bean);
        if (beanAdapter == null) {
            beanAdapter = new BeanAdapter();
            eagerBeans.put(bean, beanAdapter);
        }
        return beanAdapter;
    }

    /**
     * Returns true if this field is annotated with @Inject
     */
    protected static boolean injectAnnotatedField(Field field) {
        return field.getAnnotation(Inject.class) != null;
    }

    /**
     * Returns true for singletons or application scoped beans
     */
    protected boolean isApplicationScopeOrSingleton(Class<?> aClass) {
        return aClass.getAnnotation(Singleton.class) != null || aClass.getAnnotation(ApplicationScoped.class) != null;
    }
}
