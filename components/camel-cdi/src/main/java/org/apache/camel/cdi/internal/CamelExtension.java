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
package org.apache.camel.cdi.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consume;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiBeanManagerHelper;
import org.apache.camel.cdi.CdiCamelContext;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.impl.DefaultCamelBeanPostProcessor;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.deltaspike.core.util.metadata.builder.AnnotatedTypeBuilder;

/**
 * Set of camel specific hooks for CDI.
 */
public class CamelExtension implements Extension {

    private static class InjectLiteral extends AnnotationLiteral<Inject> {
        private static final long serialVersionUID = 1L;
    }

    @Inject
    BeanManager beanManager;

    private CamelContextMap camelContextMap;
    private final Set<Bean<?>> eagerBeans = new HashSet<Bean<?>>();
    private final Map<String, CamelContextConfig> camelContextConfigMap = new HashMap<String, CamelContextConfig>();
    private final List<CamelContextBean> camelContextBeans = new ArrayList<CamelContextBean>();

    public CamelExtension() {
    }

    /**
     * If no context name is specified then default it to the value from
     * the {@link org.apache.camel.cdi.ContextName} annotation
     */
    public static String getCamelContextName(String context, ContextName annotation) {
        if (ObjectHelper.isEmpty(context) && annotation != null) {
            return annotation.value();
        }
        return context;
    }

    /**
     * Process camel context aware bean definitions.
     *
     * @param process Annotated type.
     * @throws Exception In case of exceptions.
     */
    protected void contextAwareness(@Observes ProcessAnnotatedType<CamelContextAware> process) throws Exception {
        AnnotatedType<CamelContextAware> at = process.getAnnotatedType();

        Method method = at.getJavaClass().getMethod("setCamelContext", CamelContext.class);
        AnnotatedTypeBuilder<CamelContextAware> builder = new AnnotatedTypeBuilder<CamelContextAware>()
                .readFromType(at)
                .addToMethod(method, new InjectLiteral());
        process.setAnnotatedType(builder.create());
    }

    protected void detectRouteBuilders(@Observes ProcessAnnotatedType<?> process) throws Exception {
        AnnotatedType<?> annotatedType = process.getAnnotatedType();
        ContextName annotation = annotatedType.getAnnotation(ContextName.class);
        Class<?> javaClass = annotatedType.getJavaClass();
        if (annotation != null && isRoutesBean(javaClass)) {
            addRouteBuilderBean(annotatedType, annotation);
        }
    }

    private void addRouteBuilderBean(final AnnotatedType<?> process, ContextName annotation) {
        final CamelContextConfig config = getCamelConfig(annotation.value());
        config.addRouteBuilderBean(process);
    }

    /**
     * Disable creation of default CamelContext bean and rely on context created
     * and managed by extension.
     *
     * @param process Annotated type.
     */
    protected void disableDefaultContext(@Observes ProcessAnnotatedType<? extends CamelContext> process) {
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
        if (camelContextConfigMap.isEmpty()) {
            abd.addBean(new CamelContextBean(manager));
        } else {
            Set<Map.Entry<String, CamelContextConfig>> entries = camelContextConfigMap.entrySet();
            for (Map.Entry<String, CamelContextConfig> entry : entries) {
                String name = entry.getKey();
                CamelContextConfig config = entry.getValue();
                CamelContextBean camelContextBean = new CamelContextBean(manager, "CamelContext:" + name, name, config);
                camelContextBeans.add(camelContextBean);
                abd.addBean(camelContextBean);
            }
        }
    }

    /**
     * Lets detect all beans annotated with @Consume so they can be auto-registered
     */
    public void detectConsumeBeans(@Observes ProcessBean<?> event) {
        final Bean<?> bean = event.getBean();
        Class<?> beanClass = bean.getBeanClass();
        ReflectionHelper.doWithMethods(beanClass, new ReflectionHelper.MethodCallback() {
            @Override
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                Consume consume = method.getAnnotation(Consume.class);
                if (consume != null) {
                    eagerBeans.add(bean);
                }
            }
        });
    }

    /**
     * Lets detect all beans annotated of type {@link RouteBuilder}
     * which are annotated with {@link org.apache.camel.cdi.ContextName}
     * so they can be auto-registered
     */
    public void detectRouteBuilderBeans(@Observes ProcessBean<?> event) {
        final Bean<?> bean = event.getBean();
        Class<?> beanClass = bean.getBeanClass();
        if (isRoutesBean(beanClass)) {
            addRouteBuilderBean(bean, beanClass.getAnnotation(ContextName.class));
        }
    }

    private void addRouteBuilderBean(Bean<?> bean, ContextName annotation) {
        if (annotation != null) {
            String contextName = annotation.value();
            CamelContextConfig config = getCamelConfig(contextName);
            config.addRouteBuilderBean(bean);
        }
    }

    private CamelContextConfig getCamelConfig(final String contextName) {
        CamelContextConfig config = camelContextConfigMap.get(contextName);
        if (config == null) {
            config = new CamelContextConfig();
            camelContextConfigMap.put(contextName, config);
        }
        return config;
    }

    /**
     * Lets detect all producer methods creating instances of {@link RouteBuilder} which are annotated with
     * {@link org.apache.camel.cdi.ContextName} so they can be auto-registered
     */
    public void detectProducerRoutes(@Observes ProcessProducerMethod<?, ?> event) {
        Annotated annotated = event.getAnnotated();
        ContextName annotation = annotated.getAnnotation(ContextName.class);
        Class<?> returnType = event.getAnnotatedProducerMethod().getJavaMember().getReturnType();
        if (isRoutesBean(returnType)) {
            addRouteBuilderBean(event.getBean(), annotation);
        }
    }

    /**
     * Lets force the CDI container to create all beans annotated with @Consume so that the consumer becomes active
     */
    public void startConsumeBeans(@Observes AfterDeploymentValidation event, BeanManager beanManager) throws Exception {
        for (CamelContextBean bean : camelContextBeans) {
            String name = bean.getCamelContextName();
            CamelContext context = getCamelContext(name, beanManager);
            if (context == null) {
                throw new IllegalStateException(
                        "CamelContext '" + name + "' has not been injected into the CamelContextMap");
            }
            bean.configureCamelContext((CdiCamelContext) context);
        }

        for (Bean<?> bean : eagerBeans) {
            // force lazy creation to start the consumer
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
    }

    /**
     * Lets perform injection of all beans which use Camel annotations
     */
    @SuppressWarnings("unchecked")
    public void onInjectionTarget(@Observes ProcessInjectionTarget<?> event) {
        final InjectionTarget injectionTarget = event.getInjectionTarget();
        AnnotatedType annotatedType = event.getAnnotatedType();
        final Class<Object> beanClass = annotatedType.getJavaClass();
        // TODO this is a bit of a hack - what should the bean name be?
        final String beanName = injectionTarget.toString();
        ContextName contextName = annotatedType.getAnnotation(ContextName.class);
        final BeanAdapter adapter = createBeanAdapter(beanClass, contextName);
        if (!adapter.isEmpty()) {
            DelegateInjectionTarget newTarget = new DelegateInjectionTarget(injectionTarget) {
                @Override
                public void inject(Object instance, CreationalContext ctx) {
                    super.inject(instance, ctx);

                    // now lets inject our Camel injections to the bean instance
                    adapter.inject(CamelExtension.this, instance, beanName);
                }
            };
            event.setInjectionTarget(newTarget);
        }
    }

    /**
     * Perform injection on an existing bean such as a test case which is created directly by a testing framework.
     * <p/>
     * This is because BeanProvider.injectFields() does not invoke the onInjectionTarget() method so the injection
     * of @Produce / @EndpointInject and processing of the @Consume annotations are not performed.
     */
    public void inject(Object bean) {
        Class<?> beanClass = bean.getClass();
        ContextName contextName = beanClass.getAnnotation(ContextName.class);
        final BeanAdapter adapter = createBeanAdapter(beanClass, contextName);
        if (!adapter.isEmpty()) {
            // TODO this is a bit of a hack - what should the bean name be?
            final String beanName = bean.toString();
            adapter.inject(this, bean, beanName);
        }
    }

    private BeanAdapter createBeanAdapter(Class<?> beanClass, ContextName contextName) {
        final BeanAdapter adapter = new BeanAdapter(contextName);
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

    protected DefaultCamelBeanPostProcessor getPostProcessor(String context, BeanManager beanManager) {
        CamelContext camelContext = getCamelContext(context, beanManager);
        if (camelContext != null) {
            return new DefaultCamelBeanPostProcessor(camelContext);
        } else {
            throw new IllegalArgumentException("No such CamelContext '" + context + "' available!");
        }
    }

    protected CamelContext getCamelContext(String context, BeanManager beanManager) {
        BeanManager manager = this.beanManager != null ? this.beanManager : beanManager;
        if (camelContextMap == null && manager != null) {
            camelContextMap = CdiBeanManagerHelper.lookupBeanByType(manager, CamelContextMap.class);
        }
        ObjectHelper.notNull(camelContextMap, "Could not resolve CamelContextMap");
        return camelContextMap.getCamelContext(context);
    }

    /**
     * Returns true if this field is annotated with @Inject
     */
    protected static boolean injectAnnotatedField(Field field) {
        return field.getAnnotation(Inject.class) != null;
    }

    protected boolean isRoutesBean(Class<?> returnType) {
        return (RoutesBuilder.class.isAssignableFrom(returnType) || RouteContainer.class.isAssignableFrom(returnType))
            && !Modifier.isAbstract(returnType.getModifiers());
    }
}
