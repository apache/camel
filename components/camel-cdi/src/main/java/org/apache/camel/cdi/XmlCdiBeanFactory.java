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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.xml.bind.JAXBException;

import org.apache.camel.cdi.xml.ApplicationContextFactoryBean;
import org.apache.camel.cdi.xml.BeanManagerAware;
import org.apache.camel.cdi.xml.CamelContextFactoryBean;
import org.apache.camel.cdi.xml.ErrorHandlerDefinition;
import org.apache.camel.cdi.xml.ErrorHandlerType;
import org.apache.camel.cdi.xml.ImportDefinition;
import org.apache.camel.cdi.xml.RestContextDefinition;
import org.apache.camel.cdi.xml.RouteContextDefinition;
import org.apache.camel.core.xml.AbstractCamelFactoryBean;
import org.apache.camel.core.xml.CamelProxyFactoryDefinition;
import org.apache.camel.core.xml.CamelServiceExporterDefinition;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.util.Collections.addAll;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.ApplicationScopedLiteral.APPLICATION_SCOPED;
import static org.apache.camel.cdi.CdiSpiHelper.createCamelContextWithTCCL;
import static org.apache.camel.cdi.DefaultLiteral.DEFAULT;
import static org.apache.camel.cdi.ResourceHelper.getResource;
import static org.apache.camel.cdi.Startup.Literal.STARTUP;
import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

final class XmlCdiBeanFactory {

    private final Logger logger = LoggerFactory.getLogger(XmlCdiBeanFactory.class);

    private final BeanManager manager;

    private final CdiCamelEnvironment environment;

    private final CdiCamelExtension extension;

    private XmlCdiBeanFactory(BeanManager manager, CdiCamelEnvironment environment, CdiCamelExtension extension) {
        this.manager = manager;
        this.environment = environment;
        this.extension = extension;
    }

    static XmlCdiBeanFactory with(BeanManager manager, CdiCamelEnvironment environment, CdiCamelExtension extension) {
        return new XmlCdiBeanFactory(manager, environment, extension);
    }

    Set<SyntheticBean<?>> beansFrom(String path, AnnotatedType<?> annotatedType) throws JAXBException, IOException {
        URL url = getResource(path, annotatedType.getJavaClass().getClassLoader());
        if (url == null) {
            logger.warn("Unable to locate resource [{}] for import!", path);
            return emptySet();
        }
        return beansFrom(url, annotatedType);
    }

    Set<SyntheticBean<?>> beansFrom(URL url, AnnotatedType<?> annotatedType) throws JAXBException, IOException {
        try (InputStream xml = url.openStream()) {
            Object node = XmlCdiJaxbContexts.CAMEL_CDI.instance()
                .createUnmarshaller()
                .unmarshal(xml);
            if (node instanceof RoutesDefinition) {
                RoutesDefinition routes = (RoutesDefinition) node;
                return singleton(routesDefinitionBean(routes, url));
            } else if (node instanceof ApplicationContextFactoryBean) {
                ApplicationContextFactoryBean app = (ApplicationContextFactoryBean) node;
                Set<SyntheticBean<?>> beans = new HashSet<>();
                for (CamelContextFactoryBean factory : app.getContexts()) {
                    SyntheticBean<?> bean = camelContextBean(factory, url, annotatedType);
                    beans.add(bean);
                    beans.addAll(camelContextBeans(factory, bean, url));
                }
                for (ErrorHandlerDefinition definition : app.getErrorHandlers()) {
                    beans.add(errorHandlerBean(definition, url));
                }
                for (ImportDefinition definition : app.getImports()) {
                    // Get the base URL as imports are relative to this
                    String path = url.getFile().substring(0, url.getFile().lastIndexOf('/'));
                    String base = url.getProtocol() + "://" + url.getHost() + path;
                    beans.addAll(beansFrom(base + "/" + definition.getResource(), annotatedType));
                }
                for (RestContextDefinition factory : app.getRestContexts()) {
                    beans.add(restContextBean(factory, url));
                }
                for (RouteContextDefinition factory : app.getRouteContexts()) {
                    beans.add(routeContextBean(factory, url));
                }
                for (AbstractCamelFactoryBean<?> factory : app.getBeans()) {
                    if (hasId(factory)) {
                        beans.add(camelContextBean(null, factory, url));
                    }
                }
                return beans;
            } else if (node instanceof CamelContextFactoryBean) {
                CamelContextFactoryBean factory = (CamelContextFactoryBean) node;
                Set<SyntheticBean<?>> beans = new HashSet<>();
                SyntheticBean<?> bean = camelContextBean(factory, url, annotatedType);
                beans.add(bean);
                beans.addAll(camelContextBeans(factory, bean, url));
                return beans;
            } else if (node instanceof RestContextDefinition) {
                RestContextDefinition factory = (RestContextDefinition) node;
                return singleton(restContextBean(factory, url));
            } else if (node instanceof RouteContextDefinition) {
                RouteContextDefinition factory = (RouteContextDefinition) node;
                return singleton(routeContextBean(factory, url));
            }
        }
        return emptySet();
    }

    private SyntheticBean<?> camelContextBean(CamelContextFactoryBean factory, URL url, AnnotatedType annotatedType) {
        Set<Annotation> annotations = new HashSet<>();
        annotations.add(ANY);
        if (hasId(factory)) {
            addAll(annotations, NamedLiteral.of(factory.getId()));
        } else {
            annotations.add(DEFAULT);
            factory.setImplicitId(true);
            factory.setId(new CdiCamelContextNameStrategy().getNextName());
        }

        annotations.add(APPLICATION_SCOPED);
        SyntheticAnnotated annotated = new SyntheticAnnotated(DefaultCamelContext.class,
            manager.createAnnotatedType(DefaultCamelContext.class).getTypeClosure(),
            annotatedType.getJavaClass(),
            annotations);

        return new SyntheticBean<>(manager, annotated, DefaultCamelContext.class,
            environment.camelContextInjectionTarget(
                new SyntheticInjectionTarget<>(() -> {
                    DefaultCamelContext context = createCamelContextWithTCCL(DefaultCamelContext::new, annotated);
                    factory.setContext(context);
                    factory.setBeanManager(manager);
                    return context;
                }, context -> {
                    try {
                        factory.afterPropertiesSet();
                    } catch (Exception cause) {
                        throw new CreationException(cause);
                    }
                }),
                annotated, manager, extension), bean ->
                "imported Camel context with "
                + (factory.isImplicitId() ? "implicit " : "")
                + "id [" + factory.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers());
    }

    private Set<SyntheticBean<?>> camelContextBeans(CamelContextFactoryBean factory, Bean<?> context, URL url) {
        Set<SyntheticBean<?>> beans = new HashSet<>();

        // TODO: WARN log if the definition doesn't have an id
        if (factory.getBeansFactory() != null) {
            factory.getBeansFactory().stream()
                .filter(XmlCdiBeanFactory::hasId)
                .map(bean -> camelContextBean(context, bean, url))
                .forEach(beans::add);
        }

        // TODO: define in beans
        if (factory.getEndpoints() != null) {
            factory.getEndpoints().stream()
                .filter(XmlCdiBeanFactory::hasId)
                .map(endpoint -> camelContextBean(context, endpoint, url))
                .forEach(beans::add);
        }

        if (factory.getErrorHandlers() != null) {
            factory.getErrorHandlers().stream()
                .filter(XmlCdiBeanFactory::hasId)
                .map(handler -> errorHandlerBean(handler, url))
                .forEach(beans::add);
        }

        if (factory.getExports() != null) {
            factory.getExports().stream()
                .map(export -> serviceExporterBean(context, export, url))
                .forEach(beans::add);
        }

        if (factory.getProxies() != null) {
            factory.getProxies().stream()
                .filter(XmlCdiBeanFactory::hasId)
                .map(proxy -> proxyFactoryBean(context, proxy, url))
                .forEach(beans::add);
        }

        // TODO: define in beans
        if (factory.getRedeliveryPolicies() != null) {
            factory.getRedeliveryPolicies().stream()
                .filter(XmlCdiBeanFactory::hasId)
                .map(policy -> camelContextBean(context, policy, url))
                .forEach(beans::add);
        }

        return beans;
    }

    private SyntheticBean<?> camelContextBean(Bean<?> context, AbstractCamelFactoryBean<?> factory, URL url) {
        if (factory instanceof BeanManagerAware) {
            ((BeanManagerAware) factory).setBeanManager(manager);
        }

        Set<Annotation> annotations = new HashSet<>();
        annotations.add(ANY);
        annotations.add(hasId(factory) ? NamedLiteral.of(factory.getId()) : DEFAULT);

        // TODO: should that be @Singleton to enable injection points with bean instance type?
        if (factory.isSingleton()) {
            annotations.add(APPLICATION_SCOPED);
        }

        return new SyntheticBean<>(manager,
            new SyntheticAnnotated(factory.getObjectType(),
                manager.createAnnotatedType(factory.getObjectType()).getTypeClosure(),
                annotations),
            factory.getObjectType(),
            new XmlFactoryBeanInjectionTarget<>(manager, factory, context), bean ->
                "imported bean [" + factory.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers());
    }

    private SyntheticBean<?> proxyFactoryBean(Bean<?> context, CamelProxyFactoryDefinition proxy, URL url) {
        if (isEmpty(proxy.getServiceUrl())) {
            throw new CreationException(
                format("Missing serviceUrl attribute for imported bean [%s] from resource [%s]", proxy.getId(), url));
        }

        return new XmlProxyFactoryBean<>(manager,
            new SyntheticAnnotated(proxy.getServiceInterface(),
                manager.createAnnotatedType(proxy.getServiceInterface()).getTypeClosure(),
                APPLICATION_SCOPED, ANY, NamedLiteral.of(proxy.getId())),
            proxy.getServiceInterface(), bean ->
                "imported bean [" + proxy.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers(),
            context, proxy);
    }

    private SyntheticBean<?> serviceExporterBean(Bean<?> context, CamelServiceExporterDefinition exporter, URL url) {
        // TODO: replace with CreationException
        requireNonNull(exporter.getServiceRef(),
            () -> format("Missing [%s] attribute for imported bean [%s] from resource [%s]",
                "serviceRef", Objects.toString(exporter.getId(), "export"), url));

        Class<?> type;
        if (exporter.getServiceInterface() != null) {
            type = exporter.getServiceInterface();
        } else {
            Bean<?> bean = manager.resolve(manager.getBeans(exporter.getServiceRef()));
            if (bean != null) {
                type = bean.getBeanClass();
            } else {
                requireNonNull(exporter.getServiceInterface(),
                    () -> format("Missing [%s] attribute for imported bean [%s] from resource [%s]",
                        "serviceInterface", Objects.toString(exporter.getId(), "export"), url));
                type = exporter.getServiceInterface();
            }
        }

        Set<Type> types = new HashSet<>(manager.createAnnotatedType(type).getTypeClosure());
        // Weld does not add Object.class for interfaces as they do not extend Object.class.
        // Though let's add it so that it's possible to lookup by bean type Object.class
        // beans whose bean class is an interface (for startup beans).
        types.add(Object.class);

        return new XmlServiceExporterBean<>(manager,
            new SyntheticAnnotated(type, types, APPLICATION_SCOPED, ANY, STARTUP,
                hasId(exporter) ? NamedLiteral.of(exporter.getId()) : DEFAULT),
            type, bean ->
                "imported bean [" + Objects.toString(exporter.getId(), "export") + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers(),
            context, exporter);
    }

    private SyntheticBean<?> restContextBean(RestContextDefinition definition, URL url) {
        requireNonNull(definition.getId(),
            () -> format("Missing [%s] attribute for imported bean [%s] from resource [%s]",
                "id", "restContext", url));

        return new SyntheticBean<>(manager,
            new SyntheticAnnotated(List.class,
                Stream.of(List.class, new ListParameterizedType(RestDefinition.class))
                    .collect(toSet()),
                ANY, NamedLiteral.of(definition.getId())),
            List.class,
            new SyntheticInjectionTarget<>(definition::getRests), bean ->
                "imported rest context with "
                + "id [" + definition.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers());
    }

    private SyntheticBean<?> routeContextBean(RouteContextDefinition definition, URL url) {
        requireNonNull(definition.getId(),
            () -> format("Missing [%s] attribute for imported bean [%s] from resource [%s]",
                "id", "routeContext", url));

        return new SyntheticBean<>(manager,
            new SyntheticAnnotated(List.class,
                Stream.of(List.class, new ListParameterizedType(RouteDefinition.class))
                    .collect(toSet()),
                ANY, NamedLiteral.of(definition.getId())),
            List.class,
            new SyntheticInjectionTarget<>(definition::getRoutes), bean ->
                "imported route context with "
                + "id [" + definition.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers());
    }

    private SyntheticBean<?> routesDefinitionBean(RoutesDefinition definition, URL url) {
        return new SyntheticBean<>(manager,
            // TODO: should be @Named if the id is set
            new SyntheticAnnotated(RoutesDefinition.class,
                manager.createAnnotatedType(RoutesDefinition.class).getTypeClosure(),
                ANY, DEFAULT),
            RoutesDefinition.class,
            new SyntheticInjectionTarget<>(() -> definition), bean ->
                "imported routes definition "
                + (hasId(definition) ? "[" + definition.getId() + "] " : "")
                + "from resource [" + url + "]");
    }

    private SyntheticBean<?> errorHandlerBean(ErrorHandlerDefinition definition, URL url) {
        ErrorHandlerType type = definition.getType();

        // Validate attributes according to type
        if (isNotEmpty(definition.getDeadLetterUri())
            && !type.equals(ErrorHandlerType.DeadLetterChannel)) {
            throw attributeNotSupported("deadLetterUri", type, definition.getId());
        }

        if (isNotEmpty(definition.getDeadLetterHandleNewException())
            && !type.equals(ErrorHandlerType.DeadLetterChannel)) {
            throw attributeNotSupported("deadLetterHandleNewException", type, definition.getId());
        }

        if (isNotEmpty(definition.getTransactionTemplateRef())
            && !type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            throw attributeNotSupported("transactionTemplateRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getTransactionManagerRef())
            && !type.equals(ErrorHandlerType.TransactionErrorHandler)) {
            throw attributeNotSupported("transactionManagerRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getRollbackLoggingLevel())
            && (!type.equals(ErrorHandlerType.TransactionErrorHandler))) {
            throw attributeNotSupported("rollbackLoggingLevel", type, definition.getId());
        }

        if (isNotEmpty(definition.getUseOriginalMessage())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("useOriginalMessage", type, definition.getId());
        }

        if (isNotEmpty(definition.getUseOriginalBody())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("useOriginalBody", type, definition.getId());
        }

        if (isNotEmpty(definition.getOnRedeliveryRef())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("onRedeliveryRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getOnExceptionOccurredRef())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("onExceptionOccurredRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getOnPrepareFailureRef())
            && (type.equals(ErrorHandlerType.TransactionErrorHandler)
            || type.equals(ErrorHandlerType.NoErrorHandler))) {
            throw attributeNotSupported("onPrepareFailureRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getRetryWhileRef())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("retryWhileRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getOnRedeliveryRef())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("redeliveryPolicyRef", type, definition.getId());
        }

        if (isNotEmpty(definition.getExecutorServiceRef())
            && type.equals(ErrorHandlerType.NoErrorHandler)) {
            throw attributeNotSupported("executorServiceRef", type, definition.getId());
        }

        return new XmlErrorHandlerFactoryBean(manager,
            new SyntheticAnnotated(type.getTypeAsClass(),
                manager.createAnnotatedType(type.getTypeAsClass()).getTypeClosure(),
                ANY, NamedLiteral.of(definition.getId())),
            type.getTypeAsClass(), bean -> "imported error handler with "
                + "id [" + definition.getId() + "] "
                + "from resource [" + url + "] "
                + "with qualifiers " + bean.getQualifiers(),
            definition);
    }

    private static CreationException attributeNotSupported(String attribute, ErrorHandlerType type, String id) {
        return new CreationException(
            format("Attribute [%s] is not supported by error handler type [%s], in error handler with id [%s]",
                attribute, type, id));
    }

    private static <T extends IdentifiedType> boolean hasId(T type) {
        return isNotEmpty(type.getId());
    }

    private static <T extends OptionalIdentifiedDefinition<T>> boolean hasId(T type) {
        return isNotEmpty(type.getId());
    }
}
