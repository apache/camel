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
package org.apache.camel.core.xml;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.support.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class AbstractCamelContextFactoryBeanTest {

    // any properties (abstract methods in AbstractCamelContextFactoryBean that
    // return String and receive no arguments) that do not support property
    // placeholders
    Set<String> propertiesThatAreNotPlaceholdered = Collections.singleton("{{getErrorHandlerRef}}");

    TypeConverter typeConverter = new DefaultTypeConverter(
            new DefaultPackageScanClassResolver(),
            new Injector() {
                @Override
                public <T> T newInstance(Class<T> type) {
                    return newInstance(type, false);
                }

                @Override
                public <T> T newInstance(Class<T> type, String factoryMethod) {
                    return null;
                }

                @Override
                public <T> T newInstance(Class<T> type, Class<?> factoryClass, String factoryMethod) {
                    return null;
                }

                @Override
                public <T> T newInstance(Class<T> type, boolean postProcessBean) {
                    return ObjectHelper.newInstance(type);
                }

                @Override
                public boolean supportsAutoWiring() {
                    return false;
                }
            }, false);

    // properties that should return value that can be converted to boolean
    Set<String> valuesThatReturnBoolean = new HashSet<>(
            asList("{{getStreamCache}}", "{{getDebug}}", "{{getTrace}}", "{{getBacklogTrace}}",
                    "{{getMessageHistory}}", "{{getLogMask}}", "{{getLogExhaustedMessageBody}}",
                    "{{getCaseInsensitiveHeaders}}",
                    "{{getAutoStartup}}", "{{getDumpRoutes}}", "{{getUseMDCLogging}}", "{{getUseDataType}}",
                    "{{getUseBreadcrumb}}",
                    "{{getBeanPostProcessorEnabled}}", "{{getAllowUseOriginalMessage}}",
                    "{{getLoadTypeConverters}}", "{{getTypeConverterStatisticsEnabled}}",
                    "{{getInflightRepositoryBrowseEnabled}}"));

    // properties that should return value that can be converted to long
    Set<String> valuesThatReturnLong = new HashSet<>(List.of("{{getDelayer}}"));

    public AbstractCamelContextFactoryBeanTest() throws Exception {
        ((Service) typeConverter).start();
    }

    @Test
    public void shouldSupportPropertyPlaceholdersOnAllProperties() throws Exception {
        final Set<Invocation> invocations = new LinkedHashSet<>();

        final DefaultCamelContext context = mock(DefaultCamelContext.class,
                withSettings().invocationListeners(i -> invocations.add((Invocation) i.getInvocation())));

        final ExtendedCamelContext extendedCamelContext = mock(ExtendedCamelContext.class);

        when(context.getCamelContextExtension()).thenReturn(extendedCamelContext);

        // program the property resolution in context mock
        when(context.resolvePropertyPlaceholders(anyString())).thenAnswer(invocation -> {
            final String placeholder = invocation.getArgument(0);

            // we receive the argument and check if the method should return a
            // value that can be converted to boolean
            if (valuesThatReturnBoolean.contains(placeholder) || placeholder.endsWith("Enabled}}")) {
                return "true";
            }

            // or long
            if (valuesThatReturnLong.contains(placeholder)) {
                return "1";
            }

            // else is just plain string
            return "string";
        });
        when(context.getTypeConverter()).thenReturn(typeConverter);
        when(context.getRuntimeEndpointRegistry()).thenReturn(mock(RuntimeEndpointRegistry.class));
        when(context.getManagementNameStrategy()).thenReturn(mock(ManagementNameStrategy.class));
        when(context.getExecutorServiceManager()).thenReturn(mock(ExecutorServiceManager.class));
        when(context.getInflightRepository()).thenReturn(mock(InflightRepository.class));
        when(context.getCamelContextExtension().getContextPlugin(CamelBeanPostProcessor.class))
                .thenReturn(mock(CamelBeanPostProcessor.class));

        @SuppressWarnings("unchecked")
        final AbstractCamelContextFactoryBean<ModelCamelContext> factory = mock(AbstractCamelContextFactoryBean.class);
        when(factory.getContext()).thenReturn(context);
        doCallRealMethod().when(factory).initCamelContext(context);

        final Set<String> expectedPropertiesToBeResolved = propertiesToBeResolved(factory);

        // method under test
        factory.initCamelContext(context);

        // we want to capture the arguments initCamelContext tried to resolve
        // and check if it tried to resolve all placeholders we expected
        final ArgumentCaptor<String> capturedPlaceholders = ArgumentCaptor.forClass(String.class);
        verify(context, atLeastOnce()).resolvePropertyPlaceholders(capturedPlaceholders.capture());

        // removes any properties that are not using property placeholders
        expectedPropertiesToBeResolved.removeAll(propertiesThatAreNotPlaceholdered);

        assertThat(capturedPlaceholders.getAllValues())
                .as("The expectation is that all abstract getter methods that return Strings should support property "
                    + "placeholders, and that for those will delegate to CamelContext::resolvePropertyPlaceholders, "
                    + "we captured all placeholders that tried to resolve and found differences")
                .containsAll(expectedPropertiesToBeResolved);
    }

    Set<String> propertiesToBeResolved(final AbstractCamelContextFactoryBean<ModelCamelContext> factory) {
        final Set<String> expectedPropertiesToBeResolved = new HashSet<>();

        // looks at all abstract methods in AbstractCamelContextFactoryBean that
        // do have no declared parameters and programs the mock to return
        // "{{methodName}}" on calling that method, this happens when
        // AbstractCamelContextFactoryBean::initContext invokes the programmed
        // mock, so the returned collection will be empty until initContext
        // invokes the mocked method
        stream(AbstractCamelContextFactoryBean.class.getDeclaredMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()) && m.getParameterCount() == 0).forEach(m -> {
                    try {
                        when(m.invoke(factory)).thenAnswer(invocation -> {
                            final Method method = invocation.getMethod();

                            final String name = method.getName();

                            if (String.class.equals(method.getReturnType())) {
                                final String placeholder = "{{" + name + "}}";
                                expectedPropertiesToBeResolved.add(placeholder);
                                return placeholder;
                            }

                            return null;
                        });
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ignored) {
                        // ignored
                    }
                });

        return expectedPropertiesToBeResolved;
    }

    static boolean shouldProvidePropertyPlaceholderSupport(final Method method) {
        // all abstract getter methods that return String are possibly returning
        // strings that contain property placeholders

        final boolean isAbstract = Modifier.isAbstract(method.getModifiers());
        final boolean isGetter = method.getName().startsWith("get");
        final Class<?> returnType = method.getReturnType();

        final boolean isCompatibleReturnType = String.class.isAssignableFrom(returnType);

        return isAbstract && isGetter && isCompatibleReturnType;
    }

}
