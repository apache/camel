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
package org.apache.camel.test.main.junit5;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.BreakpointSupport;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.HierarchyTraversalMode;

import static org.apache.camel.support.ObjectHelper.invokeMethod;
import static org.apache.camel.util.ReflectionHelper.doWithFields;
import static org.apache.camel.util.ReflectionHelper.getField;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods;

/**
 * An internal class representing the context of the test that is stored by the extension and closed automatically by
 * JUnit 5.
 */
final class CamelMainContext implements ExtensionContext.Store.CloseableResource {

    /**
     * The Camel context used for the test.
     */
    private final ModelCamelContext camelContext;

    /**
     * Construct a {@code CamelMainContext} with the given Camel context.
     * 
     * @param camelContext the Camel context used for the test.
     */
    private CamelMainContext(ModelCamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * @return the Camel context used for the test.
     */
    ModelCamelContext context() {
        return camelContext;
    }

    /**
     * Start the underlying Camel context.
     */
    void start() {
        camelContext.start();
    }

    @Override
    public void close() throws Exception {
        camelContext.close();
    }

    /**
     * @param  context the extension context from which all the data needed to create an instance of
     *                 {@code CamelMainContext} is extracted
     * @return         a new instance of a {@code Builder}
     */
    static Builder builder(ExtensionContext context) {
        return new Builder(context);
    }

    /**
     * An inner class used to build an instance of {@code CamelMainContext}.
     */
    static final class Builder {

        /**
         * The type of the test class to execute.
         */
        private final Class<?> requiredTestClass;
        /**
         * The annotation {@code CamelMainTest} extracted from the test class to execute.
         */
        private final CamelMainTest annotation;
        /**
         * The instance of the test class to execute.
         */
        private final Object instance;
        /**
         * The flag indicating whether JMX should be enabled.
         */
        private boolean useJmx;

        /**
         * Construct a {@code Builder} with the given extension context.
         * 
         * @param context the extension context from which all the data needed to create an instance of
         *                {@code CamelMainContext} is extracted
         */
        private Builder(ExtensionContext context) {
            this.requiredTestClass = context.getRequiredTestClass();
            this.annotation = requiredTestClass.getAnnotation(CamelMainTest.class);
            this.instance = context.getRequiredTestInstance();
        }

        Builder useJmx(boolean useJmx) {
            this.useJmx = useJmx;
            return this;
        }

        /**
         * Build the {@code CamelMainContext} and its underlying Camel context based on the data extracted from the
         * annotation {@code CamelMainTest}.
         *
         * @return           a new instance of {@code CamelMainContext} with its underlying Camel context initialized.
         * @throws Exception if an error occurs while initializing the Camel context.
         */
        CamelMainContext build() throws Exception {
            final ModelCamelContext camelContext = new DefaultCamelContext();
            final ExtendedCamelContext extendedCamelContext = camelContext.getExtension(ExtendedCamelContext.class);
            mockEndpointsIfNeeded(extendedCamelContext);
            configureShutdownTimeout(camelContext);
            configureDebuggerIfNeeded(camelContext);
            initCamelContext(camelContext);
            final CamelBeanPostProcessor beanPostProcessor = extendedCamelContext.getBeanPostProcessor();
            initInstance(beanPostProcessor);
            replaceBeansInRegistry(camelContext.getRegistry());
            applyReplaceRouteFromWith(camelContext);
            adviceRoutes(camelContext, beanPostProcessor);
            return new CamelMainContext(camelContext);
        }

        /**
         * Configure the shutdown timeout to avoid waiting for too long.
         */
        private void configureShutdownTimeout(ModelCamelContext context) {
            context.getShutdownStrategy().setTimeout(annotation.shutdownTimeout());
        }

        /**
         * Inject all the Camel related object instances into the test instance.
         */
        private void initInstance(CamelBeanPostProcessor beanPostProcessor) throws Exception {
            beanPostProcessor.postProcessBeforeInitialization(instance, requiredTestClass.getName());
            beanPostProcessor.postProcessAfterInitialization(instance, requiredTestClass.getName());
        }

        /**
         * Mock the endpoints corresponding to the patterns provided by {@link CamelMainTest#mockEndpoints()} and
         * {@link CamelMainTest#mockEndpointsAndSkip()} if any.
         */
        private void mockEndpointsIfNeeded(ExtendedCamelContext context) {
            // enable auto mocking if enabled
            final String mockEndpoints = annotation.mockEndpoints();
            if (!mockEndpoints.isEmpty()) {
                context.registerEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpoints));
            }
            final String mockEndpointsAndSkip = annotation.mockEndpointsAndSkip();
            if (!mockEndpointsAndSkip.isEmpty()) {
                context.registerEndpointCallback(new InterceptSendToMockEndpointStrategy(mockEndpointsAndSkip, true));
            }
        }

        /**
         * Configure the debug mode if the test instance is of type {@link DebuggerCallback} in a such way that the
         * callback methods {@link DebuggerCallback#debugBefore} and {@link DebuggerCallback#debugAfter} are called when
         * executing the routes.
         */
        private void configureDebuggerIfNeeded(ModelCamelContext context) {
            if (instance instanceof DebuggerCallback) {
                context.setDebugging(true);
                context.setDebugger(new DefaultDebugger());
                DebuggerCallback callback = (DebuggerCallback) instance;
                context.getDebugger().addBreakpoint(new BreakpointSupport() {
                    @Override
                    public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
                        callback.debugBefore(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                                definition.getLabel());
                    }

                    @Override
                    public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
                        callback.debugAfter(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                                definition.getLabel(), timeTaken);
                    }
                });
            }
        }

        /**
         * Initialize the given Camel context like a Camel Main application based on what could be extracted from the
         * annotation {@link CamelMainTest}.
         */
        private void initCamelContext(ModelCamelContext context) throws Exception {
            createMainForTest().init(context);
        }

        /**
         * Create a new Camel Main application for test based on what could be extracted from the annotation
         * {@link CamelMainTest}.
         */
        private MainForTest createMainForTest() {
            final MainForTest main = new MainForTest();
            configureMainClass(main);
            addConfigurationClasses(main);
            main.configure().setJmxEnabled(useJmx);
            configureOverrideProperties(main);
            configurePropertyPlaceholderLocations(main);
            invokeConfigureMethods(main);
            return main;
        }

        /**
         * Configure the main class to use in the given Camel Main application for test.
         */
        private void configureMainClass(MainForTest main) {
            final Class<?> mainClass = annotation.mainClass();
            if (mainClass != void.class) {
                main.configure().withBasePackageScan(mainClass.getPackageName());
            }
        }

        /**
         * Add the additional configuration classes to the global configuration.
         */
        private void addConfigurationClasses(MainForTest main) {
            // Add the configuration classes if any
            for (Class<? extends CamelConfiguration> configurationClass : annotation.configurationClasses()) {
                main.configure().addConfiguration(configurationClass);
            }
        }

        /**
         * Instantiate automatically all the route builders retrieved from the annotations {@link AdviceRouteMapping}
         * using the default constructor, then inject all the Camel related object instances into the created instances
         * and finally use these created instances to advice the routes corresponding to
         * {@link AdviceRouteMapping#route()}.
         *
         * @throws Exception if a route builder could not be created or initialized, or if a route could not be advised.
         */
        private void adviceRoutes(ModelCamelContext context, CamelBeanPostProcessor beanPostProcessor) throws Exception {
            for (AdviceRouteMapping adviceRouteMapping : annotation.advices()) {
                final Class<? extends RouteBuilder> adviceClass = adviceRouteMapping.advice();
                try {
                    final Constructor<?> constructor = adviceClass.getDeclaredConstructor();
                    if (constructor.trySetAccessible()) {
                        RouteBuilder advice = adviceClass.cast(constructor.newInstance());
                        beanPostProcessor.postProcessBeforeInitialization(advice, advice.getClass().getName());
                        beanPostProcessor.postProcessAfterInitialization(advice, advice.getClass().getName());
                        AdviceWith.adviceWith(context.getRouteDefinition(adviceRouteMapping.route()), context, advice);
                    } else {
                        throw new RuntimeCamelException(
                                String.format(
                                        "The default constructor of the class %s is not accessible since it is in a package that is not opened to the extension.",
                                        adviceClass.getName()));
                    }
                } catch (NoSuchMethodException e) {
                    throw new RuntimeCamelException(
                            String.format("Could not find the default constructor of the class %s.", adviceClass.getName()), e);
                } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
                    throw new RuntimeCamelException(e);
                }
            }
        }

        /**
         * For all uris and ids extracted from {@link CamelMainTest#replaceRouteFromWith()}, replace the from endpoint
         * in the route corresponding to the extracted id with the extracted uri knowing that the expected order is
         * {@code route-id-1, new-uri-1, route-id-2, ...}.
         * 
         * @throws Exception if the content of {@link CamelMainTest#replaceRouteFromWith()} doesn't have the expected
         *                   length or a route could not be advised.
         */
        private void applyReplaceRouteFromWith(ModelCamelContext context) throws Exception {
            String[] fromEndpoints = annotation.replaceRouteFromWith();
            if (fromEndpoints.length % 2 == 1) {
                throw new RuntimeCamelException(
                        "The length of the array of replaceRouteFromWith should be even, as we expect a route id followed by a uri");
            }
            for (int i = 0; i < fromEndpoints.length - 1; i += 2) {
                final String uri = fromEndpoints[i + 1];
                AdviceWith.adviceWith(context.getRouteDefinition(fromEndpoints[i]), context, new AdviceWithRouteBuilder() {
                    @Override
                    public void configure() {
                        replaceFromWith(uri);
                    }
                });
            }
        }

        /**
         * For all methods or fields annotated with {@link ReplaceInRegistry}, replace in the registry the beans
         * corresponding to their name and type.
         * 
         * @throws RuntimeCamelException if an annotated method could not be invoked or an annotated field cannot be
         *                               accessed, or if the annotated method has parameters.
         */
        private void replaceBeansInRegistry(Registry registry) {
            for (Method method : findAnnotatedMethods(requiredTestClass, ReplaceInRegistry.class,
                    HierarchyTraversalMode.TOP_DOWN)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 0) {
                    if (method.trySetAccessible()) {
                        registry.bind(method.getName(), method.getReturnType(), invokeMethod(method, instance));
                    } else {
                        throw new RuntimeCamelException(
                                String.format(
                                        "The method %s is not accessible since it is in a package that is not opened to the extension.",
                                        method.getName()));
                    }
                } else {
                    throw new RuntimeCamelException(
                            String.format("The method %s should not have any parameter.", method.getName()));
                }
            }
            doWithFields(requiredTestClass, field -> {
                if (field.isAnnotationPresent(ReplaceInRegistry.class)) {
                    if (field.trySetAccessible()) {
                        registry.bind(field.getName(), field.getType(), getField(field, instance));
                    } else {
                        throw new RuntimeCamelException(
                                String.format(
                                        "The field %s is not accessible since it is in a package that is not opened to the extension.",
                                        field.getName()));
                    }
                }
            });
        }

        /**
         * Invoke all methods annotated with {@link Configure} that have one parameter of type
         * {@link MainConfigurationProperties}.
         * 
         * @throws RuntimeCamelException if an annotated method could not be invoked or has invalid parameters.
         */
        private void invokeConfigureMethods(MainForTest main) {
            for (Method method : findAnnotatedMethods(requiredTestClass, Configure.class, HierarchyTraversalMode.TOP_DOWN)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0] == MainConfigurationProperties.class) {
                    if (method.trySetAccessible()) {
                        invokeMethod(method, instance, main.configure());
                    } else {
                        throw new RuntimeCamelException(
                                String.format(
                                        "The method %s is not accessible since it is in a package that is not opened to the extension.",
                                        method.getName()));
                    }
                } else {
                    throw new RuntimeCamelException(
                            String.format(
                                    "The method %s should have one single parameter of type MainConfigurationProperties.",
                                    method.getName()));
                }
            }
        }

        /**
         * Configures as override properties all the properties extracted from {@link CamelMainTest#properties()}
         * knowing that the expected order is {@code property-key-1, property-value-1, property-key-2, ...}.
         * 
         * @throws RuntimeCamelException if the content of {@link CamelMainTest#properties()} doesn't have the expected
         *                               length.
         */
        private void configureOverrideProperties(MainForTest main) {
            String[] properties = annotation.properties();
            if (properties.length % 2 == 1) {
                throw new RuntimeCamelException(
                        "The length of the array of properties should be even, as we expect a key followed by its value");
            }
            for (int i = 0; i < properties.length - 1; i += 2) {
                main.addOverrideProperty(properties[i], properties[i + 1]);
            }
        }

        /**
         * Configure the property placeholder locations from the content of
         * {@link CamelMainTest#propertyPlaceholderLocations()} if set otherwise from
         * {@link CamelMainTest#propertyPlaceholderFileName()} if set in such way that the locations will be in the
         * package of the test class or in the default package.
         */
        private void configurePropertyPlaceholderLocations(MainForTest main) {
            String[] locations = annotation.propertyPlaceholderLocations();
            if (locations.length == 0) {
                String fileName = annotation.propertyPlaceholderFileName();
                if (!fileName.isEmpty()) {
                    main.setPropertyPlaceholderLocations(
                            String.format("classpath:%s/%s;optional=true,classpath:%s;optional=true",
                                    requiredTestClass.getPackageName().replace('.', '/'), fileName, fileName));
                }
            } else {
                main.setPropertyPlaceholderLocations(String.join(",", locations));
            }
        }
    }
}
