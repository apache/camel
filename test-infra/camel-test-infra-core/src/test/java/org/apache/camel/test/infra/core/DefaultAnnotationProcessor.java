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
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.infra.core.ExtensionUtils.commonProviderMessage;
import static org.apache.camel.test.infra.core.ExtensionUtils.illegalAccessMessage;
import static org.apache.camel.test.infra.core.ExtensionUtils.invocationTargetMessage;

/**
 * The default implementation of the annotation processor
 */
public class DefaultAnnotationProcessor implements AnnotationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAnnotationProcessor.class);

    private final CamelContextExtension contextExtension;

    public DefaultAnnotationProcessor(CamelContextExtension contextExtension) {
        this.contextExtension = contextExtension;
    }

    @Override
    public <T> T setupContextProvider(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Class<T> target) {
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

    @Override
    public void evalMethod(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance,
            CamelContext context) {
        final Class<?> testClass = extensionContext.getTestClass().get();

        Arrays.stream(testClass.getMethods()).filter(m -> m.isAnnotationPresent(annotationClass))
                .forEach(m -> doInvokeFixture(annotationClass, m, instance, context));
    }

    @Override
    public void evalField(
            ExtensionContext extensionContext, Class<? extends Annotation> annotationClass, Object instance,
            CamelContext context) {
        final Class<?> testClass = extensionContext.getTestClass().get();

        var superClass = testClass.getSuperclass();
        while (superClass != null) {
            Arrays.stream(superClass.getDeclaredFields()).filter(m -> m.isAnnotationPresent(annotationClass))
                    .forEach(f -> doInvokeFixture(f.getAnnotation(annotationClass), f, instance, context));

            superClass = superClass.getSuperclass();
        }

        Arrays.stream(testClass.getDeclaredFields()).filter(f -> f.isAnnotationPresent(annotationClass))
                .forEach(f -> doInvokeFixture(f.getAnnotation(annotationClass), f, instance, context));
    }

    private static Object doInvokeProvider(Class<? extends Annotation> annotationClass, Method method) {
        var methodName = method.getName();
        LOG.trace("Checking instance method: {}", methodName);
        if (method.getReturnType() == null) {
            throw new RuntimeException(
                    commonProviderMessage(annotationClass, method.getDeclaringClass()) + " provider does not return any value");
        }

        try {
            return method.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(commonProviderMessage(annotationClass, method.getDeclaringClass()), e);
        }
    }

    private void doInvokeFixture(
            Class<? extends Annotation> annotationClass, Method method, Object instance, CamelContext context) {
        var methodName = method.getName();
        LOG.trace("Checking instance method: {}", methodName);
        try {
            method.invoke(instance, context);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(illegalAccessMessage(annotationClass, instance, methodName), e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(invocationTargetMessage(annotationClass, instance, methodName), e);
        }
    }

    private void doInvokeFixture(Annotation annotation, Field field, Object instance, CamelContext context) {
        var fieldName = field.getName();
        LOG.trace("Checking instance field: {}", fieldName);
        try {
            if (!Modifier.isPublic(field.getModifiers())) {
                field.setAccessible(true);
            }

            if (annotation instanceof BindToRegistry r) {
                String bindValue = r.value();

                context.getRegistry().bind(bindValue, field.get(instance));

                return;
            }

            if (annotation instanceof EndpointInject e) {
                String uri = e.value();

                if (field.getType() == MockEndpoint.class) {
                    final MockEndpoint mockEndpoint = contextExtension.getMockEndpoint(uri);

                    ObjectHelper.notNull(mockEndpoint, "mockEndpoint");
                    field.set(instance, mockEndpoint);
                } else {
                    final Endpoint endpoint = context.getEndpoint(uri);

                    ObjectHelper.notNull(endpoint, "endpoint");
                    field.set(instance, endpoint);
                }

                return;
            }

            if (annotation instanceof Produce p) {
                final ProducerTemplate producerTemplate = context.createProducerTemplate();

                producerTemplate.setDefaultEndpointUri(p.value());
                field.set(instance, producerTemplate);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(illegalAccessMessage(annotation.getClass(), instance, fieldName), e);
        }
    }
}
