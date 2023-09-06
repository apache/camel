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
package org.apache.camel.test.junit5.params;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.params.converter.DefaultArgumentConverter;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.platform.commons.util.CollectionUtils;
import org.junit.platform.commons.util.ReflectionUtils;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParameterizedExtension implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> isAnnotated(m, Test.class))
                .orElse(false);
    }

    @Override
    public java.util.stream.Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        try {
            List<Method> parameters = getParametersMethods(testClass);
            if (parameters.size() != 1) {
                throw new IllegalStateException(
                        "Class " + testClass.getName() + " should provide a single method annotated with @"
                                                + Parameters.class.getSimpleName());
            }
            Object params = parameters.iterator().next().invoke(null);
            return CollectionUtils.toStream(params)
                    .map(ParameterizedExtension::toArguments)
                    .map(Arguments::get)
                    .map(ParameterizedTemplate::new);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate test templates for class " + testClass.getName(), e);
        }
    }

    private List<Method> getParametersMethods(Class<?> testClass) {
        List<Method> parameters = java.util.stream.Stream.of(testClass.getDeclaredMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getAnnotation(Parameters.class) != null)
                .collect(Collectors.toList());
        if (parameters.isEmpty() && testClass != null) {
            return getParametersMethods(testClass.getSuperclass());
        } else {
            return parameters;
        }
    }

    private static Arguments toArguments(Object item) {
        // Nothing to do except cast.
        if (item instanceof Arguments) {
            return (Arguments) item;
        }
        // Pass all multidimensional arrays "as is", in contrast to Object[].
        // See https://github.com/junit-team/junit5/issues/1665
        if (ReflectionUtils.isMultidimensionalArray(item)) {
            return arguments(item);
        }
        // Special treatment for one-dimensional reference arrays.
        // See https://github.com/junit-team/junit5/issues/1665
        if (item instanceof Object[]) {
            return arguments((Object[]) item);
        }
        // Pass everything else "as is".
        return arguments(item);
    }

    public static class ParameterizedTemplate implements TestTemplateInvocationContext {

        private final Object[] params;

        public ParameterizedTemplate(Object[] params) {
            this.params = params;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return "[" + invocationIndex + "] "
                   + java.util.stream.Stream.of(params).map(Object::toString).collect(Collectors.joining(", "));
        }

        @Override
        public java.util.List<Extension> getAdditionalExtensions() {
            return List.of(
                    (TestInstancePostProcessor) this::postProcessTestInstance);
        }

        protected void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
            Class<?> clazz = testInstance.getClass();
            java.util.List<Field> fields = hierarchy(clazz)
                    .map(Class::getDeclaredFields)
                    .flatMap(Stream::of)
                    .filter(f -> isAnnotated(f, Parameter.class))
                    .sorted(Comparator.comparing(f -> (Integer) f.getAnnotation(Parameter.class).value()))
                    .toList();
            if (params.length != fields.size()) {
                throw new TestInstantiationException(
                        "Expected " + fields.size() + " parameters bug got " + params.length + " when instantiating "
                                                     + clazz.getName());
            }
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                f.setAccessible(true);
                f.set(testInstance, DefaultArgumentConverter.INSTANCE.convert(params[i], f.getType(), getContext()));
            }
        }

        private ParameterContext getContext() throws NoSuchMethodException {
            Executable executable = this.getClass().getConstructor(Object[].class).getParameters()[0].getDeclaringExecutable();
            ParameterContext parameterContext = mock(ParameterContext.class);
            when(parameterContext.getDeclaringExecutable()).thenReturn(executable);
            return parameterContext;
        }

        protected Stream<Class<?>> hierarchy(Class<?> clazz) {
            Class<?> superclass = clazz.getSuperclass();
            return Stream.concat(Stream.of(clazz), superclass != null ? hierarchy(superclass) : Stream.empty());
        }

    }

}
