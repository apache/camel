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
package org.apache.camel.test.infra.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A simple Camel context extension suitable for most of the simple use cases in Camel and end-user applications.
 */
public class DefaultCamelContextExtension implements CamelContextExtension {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCamelContextExtension.class);
    private final ContextLifeCycleManager lifeCycleManager;
    private CamelContext context;
    private ProducerTemplate producerTemplate;
    private ConsumerTemplate consumerTemplate;

    /**
     * Creates a new instance of the extension
     */
    public DefaultCamelContextExtension() {
        this(new DefaultContextLifeCycleManager());
    }

    /**
     * Creates a new instance of the extension with a custom {@link ContextLifeCycleManager}
     *
     * @param lifeCycleManager a life cycle manager for the context
     */
    public DefaultCamelContextExtension(ContextLifeCycleManager lifeCycleManager) {
        this.lifeCycleManager = lifeCycleManager;
    }

    /**
     * Resolves an endpoint and asserts that it is found.
     */
    public static <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String endpointUri, Class<T> endpointType) {
        T endpoint = context.getEndpoint(endpointUri, endpointType);

        assertNotNull(endpoint, "No endpoint found for URI: " + endpointUri);

        return endpoint;
    }

    private static String commonProviderMessage(Class<? extends Annotation> annotationClass, Class<?> clazz) {
        return "Unable to setup provider " + annotationClass.getSimpleName() + " on " + clazz;
    }

    private static String commonFixtureMessage(Class<? extends Annotation> annotationClass, Object instance) {
        return "Unable to setup fixture " + annotationClass.getSimpleName() + " on " + instance.getClass().getName();
    }

    private static String invocationTargetMessage(Class<? extends Annotation> annotationClass, Object instance, String methodName) {
        return commonFixtureMessage(annotationClass, instance) + " due to invocation target exception to method: " + methodName;
    }

    private static String illegalAccessMessage(Class<? extends Annotation> annotationClass, Object instance, String methodName) {
        return commonFixtureMessage(annotationClass, instance) + " due to illegal access to method: " + methodName;
    }

    protected CamelContext createCamelContext() {
        return new DefaultCamelContext();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        lifeCycleManager.afterAll(context);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        context = setupContextProvider(extensionContext, ContextProvider.class, CamelContext.class);
        if (context == null) {
            context = createCamelContext();
        }

        producerTemplate = context.createProducerTemplate();
        producerTemplate.start();

        consumerTemplate = context.createConsumerTemplate();
        consumerTemplate.start();

        lifeCycleManager.beforeAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        final Object o = extensionContext.getTestInstance().get();

        LOG.info("********************************************************************************");
        LOG.info("Testing: {} ({})", extensionContext.getDisplayName(), o.getClass().getName());

        setupFixtureFields(extensionContext, EndpointInject.class, o);
        setupFixtureFields(extensionContext, BindToRegistry.class, o);

        if (!context.isStarted()) {
            setupFixture(extensionContext, ContextFixture.class, o);
            setupFixture(extensionContext, RouteFixture.class, o);

            lifeCycleManager.beforeEach(context);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        lifeCycleManager.afterEach(context);

        final Object o = extensionContext.getTestInstance().get();
        LOG.info("Testing done: {} ({})", extensionContext.getDisplayName(), o.getClass().getName());
        LOG.info("********************************************************************************");
    }

    private <T> T setupContextProvider(ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Class<T> target) {
        final Class<?> testClass = extensionContext.getTestClass().get();

        final Optional<Method> providerMethodOpt = Arrays.stream(testClass.getMethods())
                .filter(m -> m.isAnnotationPresent(annotationClass))
                .findFirst();

        if (providerMethodOpt.isPresent()) {
            Method providerMethod = providerMethodOpt.get();

            final Object provided = doInvokeProvider(annotationClass, providerMethod);
            if (target.isInstance(provided)) {
                return target.cast(provided);
            }
        }

        return null;
    }

    private void setupFixture(ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance) {
        final Class<?> testClass = extensionContext.getTestClass().get();

        Arrays.stream(testClass.getMethods()).filter(m -> m.isAnnotationPresent(annotationClass)).forEach(m -> doInvokeFixture(annotationClass, instance, m));
    }

    private void setupFixtureFields(ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance) {
        final Class<?> testClass = extensionContext.getTestClass().get();

        var superClass = testClass.getSuperclass();
        while (superClass != null) {
            Arrays.stream(superClass.getDeclaredFields()).filter(m -> m.isAnnotationPresent(annotationClass)).forEach(f -> doInvokeFixture(f.getAnnotation(annotationClass), instance, f));

            superClass = superClass.getSuperclass();
        }

        Arrays.stream(testClass.getDeclaredFields()).filter(f -> f.isAnnotationPresent(annotationClass)).forEach(f -> doInvokeFixture(f.getAnnotation(annotationClass), instance, f));
    }

    private static Object doInvokeProvider(Class<? extends Annotation> annotationClass, Method m) {
        var methodName = m.getName();
        LOG.trace("Checking instance method: {}", methodName);
        if (m.getReturnType() == null) {
            throw new RuntimeException(commonProviderMessage(annotationClass, m.getDeclaringClass())
                    + " provider does not return any value");
        }

        try {
            return m.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(commonProviderMessage(annotationClass, m.getDeclaringClass()), e);
        }
    }

    private void doInvokeFixture(Class<? extends Annotation> annotationClass, Object instance, Method m) {
        var methodName = m.getName();
        LOG.trace("Checking instance method: {}", methodName);
        try {
            m.invoke(instance, context);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(illegalAccessMessage(annotationClass, instance, methodName), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(invocationTargetMessage(annotationClass, instance, methodName), e);
        }
    }

    private void doInvokeFixture(Annotation annotation, Object instance, Field f) {
        var fieldName = f.getName();
        LOG.trace("Checking instance field: {}", fieldName);
        try {
            if (!Modifier.isPublic(f.getModifiers())) {
                f.setAccessible(true);
            }

            if (annotation instanceof BindToRegistry r) {
                String bindValue = r.value();

                context.getRegistry().bind(bindValue, f.get(instance));

                return;
            }

            if (annotation instanceof EndpointInject e) {
                String uri = e.value();

                if (f.getType() == MockEndpoint.class) {
                    final MockEndpoint mockEndpoint = getMockEndpoint(uri);

                    assert mockEndpoint != null;

                    f.set(instance, mockEndpoint);
                } else {
                    final Endpoint endpoint = context.getEndpoint(uri);

                    assert endpoint != null;
                    f.set(instance, endpoint);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(illegalAccessMessage(annotation.getClass(), instance, fieldName), e);
        }
    }

    @Override
    public CamelContext getContext() {
        return context;
    }

    @Override
    public ProducerTemplate getProducerTemplate() {
        return producerTemplate;
    }

    @Override
    public ConsumerTemplate getConsumerTemplate() {
        return consumerTemplate;
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an exception is thrown
     *
     * @param uri          the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @param endpointType the endpoint type (i.e., its class) to resolve
     * @return the endpoint
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return resolveMandatoryEndpoint(context, uri, endpointType);
    }

    @Override
    public MockEndpoint getMockEndpoint(String uri) {
        MockEndpoint mock = getMockEndpoint(uri, true);

        return mock;
    }

    @Override
    public MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        // look for existing mock endpoints that have the same queue name, and
        // to
        // do that we need to normalize uri and strip out query parameters and
        // whatnot
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        // strip query
        int idx = n.indexOf('?');
        if (idx != -1) {
            n = n.substring(0, idx);
        }
        final String target = n;

        // lookup endpoints in registry and try to find it
        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream().filter(e -> e instanceof MockEndpoint).filter(e -> {
            String t = e.getEndpointUri();
            // strip query
            int idx2 = t.indexOf('?');
            if (idx2 != -1) {
                t = t.substring(0, idx2);
            }
            return t.equals(target);
        }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return resolveMandatoryEndpoint(uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }
}
