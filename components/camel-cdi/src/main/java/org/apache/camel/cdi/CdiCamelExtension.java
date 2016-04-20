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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.newSetFromMap;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.AbstractExchangeEvent;
import org.apache.camel.model.RouteContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.ApplicationScopedLiteral.APPLICATION_SCOPED;
import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.camel.cdi.BeanManagerHelper.getReferencesByType;
import static org.apache.camel.cdi.CdiSpiHelper.getQualifiers;
import static org.apache.camel.cdi.CdiSpiHelper.getRawType;
import static org.apache.camel.cdi.CdiSpiHelper.hasAnnotation;
import static org.apache.camel.cdi.CdiSpiHelper.isAnnotationType;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;
import static org.apache.camel.cdi.Excluded.EXCLUDED;
import static org.apache.camel.cdi.Startup.Literal.STARTUP;

public class CdiCamelExtension implements Extension {

    private final Logger logger = LoggerFactory.getLogger(CdiCamelExtension.class);

    private final CdiCamelEnvironment environment = new CdiCamelEnvironment();

    private final Set<Class<?>> converters = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<AnnotatedType<?>> camelBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<AnnotatedType<?>> eagerBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Map<InjectionPoint, ForwardingObserverMethod<?>> cdiEventEndpoints = new ConcurrentHashMap<>();

    private final Set<Bean<?>> contextBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<Annotation> contextQualifiers = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<Annotation> eventQualifiers = newSetFromMap(new ConcurrentHashMap<>());

    private final Map<Method, Bean<?>> producerBeans = new ConcurrentHashMap<>();

    private final Map<Method, Set<Annotation>> producerQualifiers = new ConcurrentHashMap<>();

    private final Set<ContextName> contextNames = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<ImportResource> resources = newSetFromMap(new ConcurrentHashMap<>());

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
        if (hasAnnotation(pat.getAnnotatedType(), ImportResource.class)) {
            resources.add(pat.getAnnotatedType().getAnnotation(ImportResource.class));
        }
    }

    private void camelContextAware(@Observes ProcessAnnotatedType<? extends CamelContextAware> pat) {
        camelBeans.add(pat.getAnnotatedType());
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

    private void cdiEventEndpoints(@Observes ProcessBean<?> pb) {
        for (InjectionPoint ip : pb.getBean().getInjectionPoints()) {
            if (!CdiEventEndpoint.class.equals(getRawType(ip.getType()))) {
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
            Class<?> type = getRawType(am.getBaseType());
            if (Endpoint.class.isAssignableFrom(type)
                || ConsumerTemplate.class.equals(type)
                || ProducerTemplate.class.equals(type)) {
                Set<Annotation> qualifiers = getQualifiers(am, manager);
                producerQualifiers.put(am.getJavaMember(), qualifiers);
                Set<Annotation> annotations = new HashSet<>(am.getAnnotations());
                annotations.removeAll(qualifiers);
                annotations.add(EXCLUDED);
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
            eventQualifiers.addAll(pom.getObserverMethod().getObservedQualifiers().isEmpty() ? Collections.singleton(ANY) : pom.getObserverMethod().getObservedQualifiers());
        }
    }

    private <T extends RoutesBuilder> void routeBuilderBeans(@Observes ProcessBean<T> pb) {
        if (pb.getAnnotated().isAnnotationPresent(ContextName.class)) {
            contextNames.add(pb.getAnnotated().getAnnotation(ContextName.class));
        }
    }

    private <T extends CamelContext> void camelContextBeans(@Observes ProcessBean<T> pb) {
        contextBeans.add(pb.getBean());
    }

    private <T extends CamelContext> void camelContextProducerFields(@Observes ProcessProducerField<T, ?> pb) {
        contextBeans.add(pb.getBean());
    }

    private <T extends CamelContext> void camelContextProducerMethods(@Observes ProcessProducerMethod<T, ?> pb) {
        contextBeans.add(pb.getBean());
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        // The set of programmatic beans to be added
        Set<SyntheticBean<?>> beans = new HashSet<>();

        // Add beans from Camel XML resources
        for (ImportResource resource : resources) {
            XmlCdiBeanFactory factory = XmlCdiBeanFactory.with(manager, environment, this);
            for (String path : resource.value()) {
                try {
                    beans.addAll(factory.beansFrom(path));
                } catch (NoClassDefFoundError cause) {
                    if (cause.getMessage().contains("AbstractCamelContextFactoryBean")) {
                        logger.error("Importing Camel XML requires to have the 'camel-core-xml' dependency in the classpath!");
                    }
                    throw cause;
                } catch (Exception cause) {
                    abd.addDefinitionError(
                        new InjectionException(
                            "Error while importing resource [" + ResourceHelper.getResource(path) + "]", cause));
                }
            }
        }

        // The set of all the Camel context beans
        Set<Bean<?>> contexts = new HashSet<>();

        // From the discovered annotated types
        contextBeans.stream()
            .peek(contexts::add)
            .map(Bean::getQualifiers)
            .forEach(contextQualifiers::addAll);

        // From the imported Camel XML
        beans.stream()
            .filter(bean -> bean.getTypes().contains(CamelContext.class))
            .peek(contexts::add)
            .map(Bean::getQualifiers)
            .forEach(contextQualifiers::addAll);

        // From the @ContextName qualifiers on RoutesBuilder beans
        contextNames.stream()
            .filter(name -> !contextQualifiers.contains(name))
            .peek(contextQualifiers::add)
            .map(name -> camelContextBean(manager, ANY, name, APPLICATION_SCOPED))
            .peek(contexts::add)
            .forEach(beans::add);

        if (contexts.size() == 0) {
            // Add @Default Camel context bean if any
            beans.add(camelContextBean(manager, ANY, DEFAULT, APPLICATION_SCOPED));
        } else if (contexts.size() == 1) {
            // Add the @Default qualifier if there is only one Camel context bean
            Bean<?> context = contexts.iterator().next();
            if (!context.getQualifiers().contains(DEFAULT)) {
                // Only decorate if that's a programmatic bean
                if (context instanceof SyntheticBean) {
                    ((SyntheticBean<?>) context).addQualifier(DEFAULT);
                }
            }
        }

        // Finally add the beans to the deployment
        beans.forEach(abd::addBean);

        // Update the CDI Camel factory beans
        Set<Annotation> endpointQualifiers = cdiEventEndpoints.keySet().stream()
            .flatMap(ip -> ip.getQualifiers().stream())
            .collect(Collectors.toSet());
        Set<Annotation> templateQualifiers = contextQualifiers.stream()
            .filter(isAnnotationType(Default.class).or(isAnnotationType(Named.class)).negate())
            .collect(Collectors.toSet());
        // TODO: would be more correct to add a bean for each Camel context bean
        producerBeans.entrySet().stream()
            .map(producer -> new BeanDelegate<>(producer.getValue(),
                producerQualifiers.get(producer.getKey()),
                CdiEventEndpoint.class.equals(producer.getKey().getReturnType())
                    ? endpointQualifiers
                    : templateQualifiers))
            .forEach(abd::addBean);

        // Add CDI event endpoint observer methods
        for (ObserverMethod method : cdiEventEndpoints.values()) {
            abd.addObserverMethod(method);
        }
    }

    private SyntheticBean<?> camelContextBean(BeanManager manager, Annotation... qualifiers) {
        SyntheticAnnotated annotated = new SyntheticAnnotated(DefaultCamelContext.class,
            manager.createAnnotatedType(DefaultCamelContext.class).getTypeClosure(), qualifiers);
        return new SyntheticBean<>(manager, annotated, DefaultCamelContext.class,
            environment.camelContextInjectionTarget(
                new SyntheticInjectionTarget<>(DefaultCamelContext::new), annotated, manager, this), bean ->
            "Default Camel context bean with qualifiers " + bean.getQualifiers());
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        Collection<CamelContext> contexts = new ArrayList<>();
        for (Bean<?> context : manager.getBeans(CamelContext.class, ANY)) {
            contexts.add(getReference(manager, CamelContext.class, context));
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
        Set<Bean<?>> routes = new HashSet<>(manager.getBeans(RoutesBuilder.class, ANY));
        routes.addAll(manager.getBeans(RouteContainer.class, ANY));
        for (Bean<?> context : manager.getBeans(CamelContext.class, ANY)) {
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

        // Trigger eager beans instantiation (calling toString is necessary to force
        // the initialization of normal-scoped beans).
        // FIXME: This does not work with OpenWebBeans for bean whose bean type is an
        // interface as the Object methods does not get forwarded to the bean instances!
        for (AnnotatedType<?> type : eagerBeans) {
            // Calling toString is necessary to force the initialization of normal-scoped beans
            getReferencesByType(manager, type.getJavaClass(), ANY).toString();
        }
        manager.getBeans(Object.class, ANY, STARTUP).stream()
            .forEach(bean -> getReference(manager, bean.getBeanClass(), bean).toString());

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
        Stream.of(converters, camelBeans, eagerBeans, contextBeans, contextNames).forEach(Set::clear);
        Stream.of(producerBeans, producerQualifiers).forEach(Map::clear);
    }

    private boolean addRouteToContext(Bean<?> routeBean, Bean<?> contextBean, BeanManager manager, AfterDeploymentValidation adv) {
        try {
            CamelContext context = getReference(manager, CamelContext.class, contextBean);
            try {
                Object route = getReference(manager, Object.class, routeBean);
                if (route instanceof RoutesBuilder) {
                    context.addRoutes((RoutesBuilder) route);
                } else if (route instanceof RouteContainer) {
                    context.addRouteDefinitions(((RouteContainer) route).getRoutes());
                } else {
                    throw new IllegalArgumentException(
                        "Invalid routes type [" + routeBean.getBeanClass().getName() + "], "
                            + "must be either of type RoutesBuilder or RouteContainer!");
                }
                return true;
            } catch (Exception cause) {
                adv.addDeploymentProblem(
                    new InjectionException(
                        "Error adding routes of type [" + routeBean.getBeanClass().getName() + "] "
                            + "to Camel context [" + context.getName() + "]", cause));
            }
        } catch (Exception exception) {
            adv.addDeploymentProblem(exception);
        }
        return false;
    }
}
