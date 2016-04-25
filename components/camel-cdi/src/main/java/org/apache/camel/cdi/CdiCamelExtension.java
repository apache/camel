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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.addAll;
import static java.util.Collections.newSetFromMap;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.inject.Named;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Consume;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.model.RouteContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.cdi.CdiSpiHelper.getFirstElementOfType;
import static org.apache.camel.cdi.CdiSpiHelper.getRawType;
import static org.apache.camel.cdi.CdiSpiHelper.hasAnnotation;
import static org.apache.camel.cdi.CdiSpiHelper.hasDefaultContext;

public class CdiCamelExtension implements Extension {

    private final Logger logger = LoggerFactory.getLogger(CdiCamelExtension.class);

    private final CdiCamelEnvironment environment = new CdiCamelEnvironment();

    private final Set<Class<?>> converters = newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    private final Set<AnnotatedType<?>> camelBeans = newSetFromMap(new ConcurrentHashMap<AnnotatedType<?>, Boolean>());

    private final Set<AnnotatedType<?>> eagerBeans = newSetFromMap(new ConcurrentHashMap<AnnotatedType<?>, Boolean>());

    private final Map<InjectionPoint, ForwardingObserverMethod<?>> cdiEventEndpoints = new ConcurrentHashMap<>();

    private final Set<Bean<?>> cdiBeans = newSetFromMap(new ConcurrentHashMap<Bean<?>, Boolean>());

    private final Set<Annotation> contextQualifiers = newSetFromMap(new ConcurrentHashMap<Annotation, Boolean>());

    private final Set<Annotation> eventQualifiers = newSetFromMap(new ConcurrentHashMap<Annotation, Boolean>());

    private final Map<Method, Bean<?>> producerBeans = new ConcurrentHashMap<>();

    private final Map<Method, Set<Annotation>> producerQualifiers = new ConcurrentHashMap<>();

    ForwardingObserverMethod<?> getObserverMethod(InjectionPoint ip) {
        return cdiEventEndpoints.get(ip);
    }

    Set<Annotation> getObserverEvents() {
        return eventQualifiers;
    }

    Set<Annotation> getContextQualifiers() {
        return contextQualifiers;
    }

    private void processAnnotatedType(@Observes ProcessAnnotatedType<?> pat) {
        if (pat.getAnnotatedType().isAnnotationPresent(Vetoed.class)) {
            pat.veto();
        }
        if (hasAnnotation(pat.getAnnotatedType(), Converter.class)) {
            converters.add(pat.getAnnotatedType().getJavaClass());
        }
        if (hasAnnotation(pat.getAnnotatedType(), BeanInject.class, Consume.class, EndpointInject.class, Produce.class, PropertyInject.class)) {
            camelBeans.add(pat.getAnnotatedType());
        }
        if (hasAnnotation(pat.getAnnotatedType(), Consume.class)) {
            eagerBeans.add(pat.getAnnotatedType());
        }
    }

    private <T extends CamelContext> void camelContextBeans(@Observes ProcessInjectionTarget<T> pit, BeanManager manager) {
        pit.setInjectionTarget(environment.camelContextInjectionTarget(pit.getInjectionTarget(), pit.getAnnotatedType(), manager, this));
    }

    private <T extends CamelContext> void camelContextProducers(@Observes ProcessProducer<?, T> pp, BeanManager manager) {
        pp.setProducer(environment.camelContextProducer(pp.getProducer(), pp.getAnnotatedMember(), manager, this));
    }

    private <T> void camelBeansPostProcessor(@Observes ProcessInjectionTarget<T> pit, BeanManager manager) {
        if (camelBeans.contains(pit.getAnnotatedType())) {
            pit.setInjectionTarget(new CamelBeanInjectionTarget<>(pit.getInjectionTarget(), manager));
        }
    }

    private <T extends CamelContextAware> void camelContextAware(@Observes ProcessInjectionTarget<T> pit, BeanManager manager) {
        pit.setInjectionTarget(new CamelBeanInjectionTarget<>(pit.getInjectionTarget(), manager));
    }

    private void cdiEventEndpoints(@Observes ProcessBean<?> pb) {
        for (InjectionPoint ip : pb.getBean().getInjectionPoints()) {
            if (!CdiEventEndpoint.class.equals(CdiSpiHelper.getRawType(ip.getType()))) {
                continue;
            }
            // TODO: refine the key to the type and qualifiers instead of the whole injection point as it leads to registering redundant observers
            if (ip.getType() instanceof ParameterizedType) {
                cdiEventEndpoints.put(ip, new ForwardingObserverMethod<>(((ParameterizedType) ip.getType()).getActualTypeArguments()[0], ip.getQualifiers()));
            } else if (ip.getType() instanceof Class) {
                cdiEventEndpoints.put(ip, new ForwardingObserverMethod<>(Object.class, ip.getQualifiers()));
            }
        }
    }

    private <T extends Endpoint> void endpointBeans(@Observes ProcessProducerMethod<T, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void consumerTemplateBeans(@Observes ProcessProducerMethod<ConsumerTemplate, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void producerTemplateBeans(@Observes ProcessProducerMethod<ProducerTemplate, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void camelFactoryProducers(@Observes ProcessAnnotatedType<CdiCamelFactory> pat, BeanManager manager) {
        AnnotatedType<CdiCamelFactory> at = pat.getAnnotatedType();
        Set<AnnotatedMethod<? super CdiCamelFactory>> methods = new HashSet<>();
        for (AnnotatedMethod<? super CdiCamelFactory> am : pat.getAnnotatedType().getMethods()) {
            if (!am.isAnnotationPresent(Produces.class)) {
                continue;
            }
            Class<?> type = CdiSpiHelper.getRawType(am.getBaseType());
            if (Endpoint.class.isAssignableFrom(type)
                || ConsumerTemplate.class.equals(type)
                || ProducerTemplate.class.equals(type)) {
                Set<Annotation> qualifiers = CdiSpiHelper.getQualifiers(am, manager);
                producerQualifiers.put(am.getJavaMember(), qualifiers);
                Set<Annotation> annotations = new HashSet<>(am.getAnnotations());
                annotations.removeAll(qualifiers);
                annotations.add(Excluded.INSTANCE);
                methods.add(new AnnotatedMethodDelegate<>(am, annotations));
            }
        }
        pat.setAnnotatedType(new AnnotatedTypeDelegate<>(at, methods));
    }

    private <T extends EventObject> void camelEventNotifiers(@Observes ProcessObserverMethod<T, ?> pom) {
        // Only activate Camel event notifiers for explicit Camel event observers, that is, an observer method for a super type won't activate notifiers.
        Type type = pom.getObserverMethod().getObservedType();
        // Camel events are raw types
        if (type instanceof Class && Class.class.cast(type).getPackage().equals(AbstractExchangeEvent.class.getPackage())) {
            eventQualifiers.addAll(pom.getObserverMethod().getObservedQualifiers().isEmpty() ? Collections.singleton(AnyLiteral.INSTANCE) : pom.getObserverMethod().getObservedQualifiers());
        }
    }

    private void beans(@Observes ProcessProducerField<?, ?> pb) {
        cdiBeans.add(pb.getBean());
    }

    private void beans(@Observes ProcessProducerMethod<?, ?> pb) {
        cdiBeans.add(pb.getBean());
    }

    private void beans(@Observes ProcessBean<?> pb) {
        cdiBeans.add(pb.getBean());
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        Set<ContextName> contextNames = new HashSet<>();
        for (Bean<?> bean : cdiBeans) {
            if (bean.getTypes().contains(CamelContext.class)) {
                contextQualifiers.addAll(bean.getQualifiers());
            } else if (bean.getTypes().contains(RoutesBuilder.class)
                || bean.getTypes().contains(RouteContainer.class)) {
                ContextName name = getFirstElementOfType(bean.getQualifiers(), ContextName.class);
                if (name != null) {
                    contextNames.add(name);
                }
            }
        }
        contextNames.removeAll(contextQualifiers);

        if (contextQualifiers.isEmpty() && contextNames.isEmpty() && shouldDeployDefaultCamelContext(cdiBeans)) {
            // Add a @Default Camel context bean if any
            abd.addBean(camelContextBean(manager, AnyLiteral.INSTANCE, DefaultLiteral.INSTANCE));
        } else if (contextQualifiers.isEmpty() && contextNames.size() == 1) {
            // Add a @ContextName and @Default Camel context bean if only one
            ContextName name = contextNames.iterator().next();
            abd.addBean(camelContextBean(manager, AnyLiteral.INSTANCE, DefaultLiteral.INSTANCE, name));
            addAll(contextQualifiers, AnyLiteral.INSTANCE, DefaultLiteral.INSTANCE, name);
        } else {
            // Add missing @ContextName Camel context beans
            for (ContextName name : contextNames) {
                abd.addBean(camelContextBean(manager, AnyLiteral.INSTANCE, name));
                addAll(contextQualifiers, AnyLiteral.INSTANCE, name);
            }
        }

        // Then update the Camel producer beans
        for (Map.Entry<Method, Bean<?>> producer : producerBeans.entrySet()) {
            Bean<?> bean = producer.getValue();
            Set<Annotation> qualifiers = new HashSet<>(producerQualifiers.get(producer.getKey()));
            Class<?> type = producer.getKey().getReturnType();
            if (CdiEventEndpoint.class.equals(type)) {
                for (InjectionPoint ip : cdiEventEndpoints.keySet()) {
                    qualifiers.addAll(ip.getQualifiers());
                }
            } else {
                if (Endpoint.class.isAssignableFrom(type)
                    || ConsumerTemplate.class.equals(type)
                    || ProducerTemplate.class.equals(type)) {
                    qualifiers.addAll(CdiSpiHelper.excludeElementOfTypes(contextQualifiers, Default.class, Named.class));
                }
            }
            // TODO: would be more correct to add a bean for each Camel context bean
            abd.addBean(new BeanDelegate<>(bean, qualifiers));
        }

        // Add CDI event endpoint observer methods
        for (ObserverMethod method : cdiEventEndpoints.values()) {
            abd.addObserverMethod(method);
        }
    }

    private boolean shouldDeployDefaultCamelContext(Set<Bean<?>> beans) {
        // Is there a Camel bean with the @Default qualifier?
        for (Bean<?> bean : beans) {
            if (bean.getBeanClass().getPackage().equals(getClass().getPackage())) {
                continue;
            }
            if (bean.getTypes().contains(CamelContextAware.class)
                || bean.getTypes().contains(Component.class)
                || bean.getTypes().contains(RouteContainer.class)
                || bean.getTypes().contains(RoutesBuilder.class)) {
                if (bean.getQualifiers().contains(DefaultLiteral.INSTANCE)) {
                    return true;
                }
            }
        }
        // Or a bean with Camel annotations?
        for (AnnotatedType<?> type : camelBeans) {
            for (Annotated field : type.getFields()) {
                if (hasDefaultContext(field)) {
                    return true;
                }
            }
            for (Annotated method : type.getMethods()) {
                if (hasDefaultContext(method)) {
                    return true;
                }
            }
        }
        // Or an injection point for Camel primitives?
        for (Bean<?> bean : beans) {
            if (bean.getBeanClass().getPackage().equals(getClass().getPackage())) {
                continue;
            }
            for (InjectionPoint ip : bean.getInjectionPoints()) {
                if (!getRawType(ip.getType()).getPackage().getName().startsWith("org.apache.camel")) {
                    continue;
                }
                for (Annotation qualifier : ip.getQualifiers()) {
                    if (qualifier.annotationType().equals(Uri.class)
                        || qualifier.annotationType().equals(Mock.class)
                        || qualifier.annotationType().equals(Default.class)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private Bean<?> camelContextBean(BeanManager manager, Annotation... qualifiers) {
        CdiCamelContextAnnotated annotated = new CdiCamelContextAnnotated(manager, qualifiers);
        return new CdiCamelContextBean(annotated, environment.camelContextInjectionTarget(new CamelContextDefaultProducer(), annotated, manager, this));
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        Collection<CamelContext> contexts = new ArrayList<>();
        for (Bean<?> context : manager.getBeans(CamelContext.class, AnyLiteral.INSTANCE)) {
            contexts.add(BeanManagerHelper.getReference(manager, CamelContext.class, context));
        }

        // Add type converters to Camel contexts
        CdiTypeConverterLoader loader = new CdiTypeConverterLoader();
        for (Class<?> converter : converters) {
            for (CamelContext context : contexts) {
                loader.loadConverterMethods(context.getTypeConverterRegistry(), converter);
            }
        }

        // Add routes to Camel contexts
        boolean deploymentException = false;
        Set<Bean<?>> routes = new HashSet<>(manager.getBeans(RoutesBuilder.class, AnyLiteral.INSTANCE));
        routes.addAll(manager.getBeans(RouteContainer.class, AnyLiteral.INSTANCE));
        for (Bean<?> context : manager.getBeans(CamelContext.class, AnyLiteral.INSTANCE)) {
            for (Bean<?> route : routes) {
                Set<Annotation> qualifiers = new HashSet<>(context.getQualifiers());
                qualifiers.retainAll(route.getQualifiers());
                if (qualifiers.size() > 1) {
                    deploymentException |= !addRouteToContext(route, context, manager, adv);
                }
            }
        }
        // Let's return to avoid starting misconfigured contexts
        if (deploymentException) {
            return;
        }

        // Trigger eager beans instantiation
        for (AnnotatedType<?> type : eagerBeans) {
            // Calling toString is necessary to force the initialization of normal-scoped beans
            BeanManagerHelper.getReferencesByType(manager, type.getJavaClass(), AnyLiteral.INSTANCE).toString();
        }

        // Start Camel contexts
        for (CamelContext context : contexts) {
            if (ServiceStatus.Started.equals(context.getStatus())) {
                continue;
            }
            logger.info("Camel CDI is starting Camel context [{}]", context.getName());
            try {
                context.start();
            } catch (Exception exception) {
                adv.addDeploymentProblem(exception);
            }
        }

        // Clean-up
        camelBeans.clear();
        cdiBeans.clear();
        converters.clear();
        eagerBeans.clear();
        producerBeans.clear();
        producerQualifiers.clear();
    }

    private boolean addRouteToContext(Bean<?> routeBean, Bean<?> contextBean, BeanManager manager, AfterDeploymentValidation adv) {
        try {
            CamelContext context = BeanManagerHelper.getReference(manager, CamelContext.class, contextBean);
            try {
                Object route = BeanManagerHelper.getReference(manager, Object.class, routeBean);
                if (route instanceof RoutesBuilder) {
                    context.addRoutes((RoutesBuilder) route);
                } else if (route instanceof RouteContainer) {
                    context.addRouteDefinitions(((RouteContainer) route).getRoutes());
                } else {
                    throw new IllegalArgumentException("Invalid routes type [" + routeBean.getBeanClass().getName() + "], must be either of type RoutesBuilder or RouteContainer!");
                }
                return true;
            } catch (Exception cause) {
                adv.addDeploymentProblem(new InjectionException("Error adding routes of type [" + routeBean.getBeanClass().getName() + "] to Camel context [" + context.getName() + "]", cause));
            }
        } catch (Exception exception) {
            adv.addDeploymentProblem(exception);
        }
        return false;
    }
}
