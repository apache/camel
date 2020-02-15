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
package org.apache.camel.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
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
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ServiceStatus;
import org.apache.camel.TypeConverter;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.spi.CamelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.newSetFromMap;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.ApplicationScopedLiteral.APPLICATION_SCOPED;
import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.camel.cdi.BeanManagerHelper.getReferencesByType;
import static org.apache.camel.cdi.CdiEventEndpoint.eventEndpointUri;
import static org.apache.camel.cdi.CdiSpiHelper.getQualifiers;
import static org.apache.camel.cdi.CdiSpiHelper.getRawType;
import static org.apache.camel.cdi.CdiSpiHelper.hasAnnotation;
import static org.apache.camel.cdi.CdiSpiHelper.hasType;
import static org.apache.camel.cdi.CdiSpiHelper.isAnnotationType;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;
import static org.apache.camel.cdi.Excluded.EXCLUDED;
import static org.apache.camel.cdi.ResourceHelper.getResource;
import static org.apache.camel.cdi.Startup.Literal.STARTUP;

public class CdiCamelExtension implements Extension {

    private final Logger logger = LoggerFactory.getLogger(CdiCamelExtension.class);

    private final CdiCamelEnvironment environment = new CdiCamelEnvironment();

    private final Set<Class<?>> converters = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<AnnotatedType<?>> camelBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<AnnotatedType<?>> eagerBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Map<String, CdiEventEndpoint<?>> cdiEventEndpoints = new ConcurrentHashMap<>();

    private final Set<Bean<?>> cdiBeans = newSetFromMap(new ConcurrentHashMap<>());

    private final Set<Annotation> contextQualifiers = newSetFromMap(new ConcurrentHashMap<>());

    private final Map<Method, Bean<?>> producerBeans = new ConcurrentHashMap<>();

    private final Map<Method, Set<Annotation>> producerQualifiers = new ConcurrentHashMap<>();

    private final Set<Annotation> eventQualifiers = newSetFromMap(new ConcurrentHashMap<>());

    private final Map<AnnotatedType<?>, ImportResource> resources = new ConcurrentHashMap<>();

    private final CdiCamelConfigurationEvent configuration = new CdiCamelConfigurationEvent();

    CdiEventEndpoint<?> getEventEndpoint(String uri) {
        return cdiEventEndpoints.get(uri);
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
            resources.put(pat.getAnnotatedType(), pat.getAnnotatedType().getAnnotation(ImportResource.class));
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

    private <T extends Endpoint> void endpointBeans(@Observes ProcessProducerMethod<T, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void consumerTemplateBeans(@Observes ProcessProducerMethod<ConsumerTemplate, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void producerTemplateBeans(@Observes ProcessProducerMethod<ProducerTemplate, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void fluentProducerTemplateBeans(@Observes ProcessProducerMethod<FluentProducerTemplate, CdiCamelFactory> ppm) {
        producerBeans.put(ppm.getAnnotatedProducerMethod().getJavaMember(), ppm.getBean());
    }

    private void camelFactoryProducers(@Observes ProcessAnnotatedType<CdiCamelFactory> pat, BeanManager manager) {
        pat.setAnnotatedType(
            new AnnotatedTypeDelegate<>(
                pat.getAnnotatedType(), pat.getAnnotatedType().getMethods().stream()
                .filter(am -> am.isAnnotationPresent(Produces.class))
                .filter(am -> am.getTypeClosure().stream().noneMatch(isEqual(TypeConverter.class)))
                .peek(am -> producerQualifiers.put(am.getJavaMember(), getQualifiers(am, manager)))
                .map(am -> new AnnotatedMethodDelegate<>(am, am.getAnnotations().stream()
                    .filter(annotation -> !manager.isQualifier(annotation.annotationType()))
                    .collect(collectingAndThen(toSet(), annotations -> {
                        annotations.add(EXCLUDED); return annotations;
                    }))))
                .collect(toSet())));
    }

    private <T extends CamelEvent> void camelEventNotifiers(@Observes ProcessObserverMethod<T, ?> pom) {
        // Only activate Camel event notifiers for explicit Camel event observers, that is, an observer method for a super type won't activate notifiers.
        Type type = pom.getObserverMethod().getObservedType();
        // Camel events are raw types
        if (type instanceof Class && CamelEvent.class.isAssignableFrom(Class.class.cast(type))) {
            Set<Annotation> qualifiers = pom.getObserverMethod().getObservedQualifiers();
            if (qualifiers.isEmpty()) {
                eventQualifiers.add(ANY);
            } else if (qualifiers.size() == 1 && qualifiers.stream()
                .anyMatch(isAnnotationType(Named.class))) {
                eventQualifiers.add(DEFAULT);
            } else {
                eventQualifiers.addAll(qualifiers);
            }
        }
    }

    private void beans(@Observes ProcessProducerField<?, ?> pb) {
        cdiBeans.add(pb.getBean());
    }

    private void beans(@Observes ProcessProducerMethod<?, ?> pb) {
        cdiBeans.add(pb.getBean());
    }

    private void beans(@Observes ProcessBean<?> pb, BeanManager manager) {
        cdiBeans.add(pb.getBean());
        // Lookup for CDI event endpoint injection points
        pb.getBean().getInjectionPoints().stream()
            .filter(ip -> CdiEventEndpoint.class.equals(getRawType(ip.getType())))
            .forEach(ip -> {
                Type type = ip.getType() instanceof ParameterizedType
                    ? ((ParameterizedType) ip.getType()).getActualTypeArguments()[0]
                    : Object.class;
                String uri = eventEndpointUri(type, ip.getQualifiers());
                cdiEventEndpoints.put(uri, new CdiEventEndpoint<>(uri, type, ip.getQualifiers(), manager));
            });
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager manager) {
        // The set of extra Camel CDI beans
        Set<SyntheticBean<?>> extraBeans = new HashSet<>();

        // Add beans from Camel XML resources
        for (AnnotatedType<?> annotatedType: resources.keySet()) {
            XmlCdiBeanFactory factory = XmlCdiBeanFactory.with(manager, environment, this);
            ImportResource resource = resources.get(annotatedType);
            for (String path : resource.value()) {
                try {
                    extraBeans.addAll(factory.beansFrom(path, annotatedType));
                } catch (NoClassDefFoundError cause) {
                    if (cause.getMessage().contains("AbstractCamelContextFactoryBean")) {
                        logger.error("Importing Camel XML requires to have the 'camel-core-xml' dependency in the classpath!");
                    }
                    throw cause;
                } catch (Exception cause) {
                    abd.addDefinitionError(
                        new InjectionException(
                            "Error while importing resource [" + getResource(path, annotatedType.getJavaClass().getClassLoader()) + "]", cause));
                }
            }
        }

        // Camel contexts from the imported Camel XML
        concat(cdiBeans.stream(), extraBeans.stream())
            .filter(hasType(CamelContext.class))
            .map(Bean::getQualifiers)
            .forEach(contextQualifiers::addAll);

        Set<Bean<?>> allBeans = concat(cdiBeans.stream(), extraBeans.stream())
            .collect(toSet());
        Set<Bean<?>> contexts = allBeans.stream()
            .filter(hasType(CamelContext.class))
            .collect(toSet());

        if (contexts.size() == 0 && shouldDeployDefaultCamelContext(allBeans)) {
            // Add @Default Camel context bean if any
            extraBeans.add(camelContextBean(manager, null, ANY, DEFAULT, APPLICATION_SCOPED));
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
        extraBeans.forEach(abd::addBean);

        // Update the CDI Camel factory beans
        Set<Annotation> endpointQualifiers = cdiEventEndpoints.values().stream()
            .map(CdiEventEndpoint::getQualifiers)
            .flatMap(Set::stream)
            .collect(toSet());
        Set<Annotation> templateQualifiers = contextQualifiers.stream()
            .filter(isAnnotationType(Default.class).or(isAnnotationType(Named.class)).negate())
            .collect(toSet());
        // TODO: would be more correct to add a bean for each Camel context bean
        producerBeans.entrySet().stream()
            .map(producer -> new BeanDelegate<>(producer.getValue(),
                producerQualifiers.get(producer.getKey()),
                CdiEventEndpoint.class.equals(producer.getKey().getReturnType())
                    ? endpointQualifiers
                    : templateQualifiers))
            .forEach(abd::addBean);

        // Add CDI event endpoint observer methods
        cdiEventEndpoints.values().stream()
            .map(ForwardingObserverMethod::new)
            .forEach(abd::addObserverMethod);
    }

    private boolean shouldDeployDefaultCamelContext(Set<Bean<?>> beans) {
        return beans.stream()
            // Is there a Camel bean with the @Default qualifier?
            // Excluding internal components...
            .filter(bean -> !bean.getBeanClass().getPackage().equals(getClass().getPackage()))
            .filter(hasType(CamelContextAware.class).or(hasType(Component.class))
                .or(hasType(RouteContainer.class).or(hasType(RoutesBuilder.class))))
            .map(Bean::getQualifiers)
            .flatMap(Set::stream)
            .anyMatch(isEqual(DEFAULT))
            // Or a bean with Camel annotations?
            || concat(camelBeans.stream().map(AnnotatedType::getFields),
                      camelBeans.stream().map(AnnotatedType::getMethods))
            .flatMap(Set::stream)
            .map(Annotated::getAnnotations)
            .flatMap(Set::stream)
            .anyMatch(isAnnotationType(Consume.class)
                .or(isAnnotationType(BeanInject.class))
                .or(isAnnotationType(EndpointInject.class))
                .or(isAnnotationType(Produce.class))
                .or(isAnnotationType(PropertyInject.class)))
            // Or an injection point for Camel primitives?
            || beans.stream()
            // Excluding internal components...
            .filter(bean -> !bean.getBeanClass().getPackage().equals(getClass().getPackage()))
            .map(Bean::getInjectionPoints)
            .flatMap(Set::stream)
            .filter(ip -> getRawType(ip.getType()).getName().startsWith("org.apache.camel"))
            .map(InjectionPoint::getQualifiers)
            .flatMap(Set::stream)
            .anyMatch(isAnnotationType(Uri.class).or(isEqual(DEFAULT)));
    }

    private SyntheticBean<?> camelContextBean(BeanManager manager, Class<?> beanClass, Annotation... qualifiers) {
        SyntheticAnnotated annotated = new SyntheticAnnotated(CdiCamelContext.class,
            manager.createAnnotatedType(CdiCamelContext.class).getTypeClosure(), beanClass, qualifiers);
        return new SyntheticBean<>(manager, annotated, CdiCamelContext.class,
            environment.camelContextInjectionTarget(
                new SyntheticInjectionTarget<>(CdiCamelContext::new), annotated, manager, this), bean ->
            "CdiCamelContext bean with qualifiers " + bean.getQualifiers());
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation adv, BeanManager manager) {
        // Send event for Camel CDI configuration
        manager.fireEvent(configuration);
        configuration.unmodifiable();

        Collection<CamelContext> contexts = new ArrayList<>();
        for (Bean<?> context : manager.getBeans(CamelContext.class, ANY)) {
            contexts.add(getReference(manager, CamelContext.class, context));
        }

        // Add type converters to Camel contexts
        for (CamelContext context : contexts) {
            CdiTypeConverterLoader loader = new CdiTypeConverterLoader();
            for (Class<?> converter : converters) {
                loader.loadConverterMethods(context.getTypeConverterRegistry(), converter);
            }
        }

        // Add routes to Camel contexts
        if (configuration.autoConfigureRoutes()) {
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
        }

        // Trigger eager beans instantiation (calling toString is necessary to force
        // the initialization of normal-scoped beans).
        // FIXME: This does not work with OpenWebBeans for bean whose bean type is an
        // interface as the Object methods does not get forwarded to the bean instances!
        eagerBeans.forEach(type -> getReferencesByType(manager, type.getJavaClass(), ANY).toString());
        manager.getBeans(Object.class, ANY, STARTUP)
            .forEach(bean -> getReference(manager, bean.getBeanClass(), bean).toString());

        // Start Camel contexts
        if (configuration.autoStartContexts()) {
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
        }

        // Clean-up
        Stream.of(converters, camelBeans, eagerBeans, cdiBeans).forEach(Set::clear);
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
                    context.getExtension(Model.class).addRouteDefinitions(((RouteContainer) route).getRoutes());
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
