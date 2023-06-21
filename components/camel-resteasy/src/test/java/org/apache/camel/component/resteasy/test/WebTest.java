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
package org.apache.camel.component.resteasy.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

import org.eclipse.jetty.deploy.App;
import org.jboss.arquillian.container.jetty.embedded_10.JettyEmbeddedConfiguration;
import org.jboss.arquillian.container.jetty.embedded_10.JettyEmbeddedContainer;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.impl.enricher.resource.URIResourceProvider;
import org.jboss.arquillian.container.test.impl.enricher.resource.URLResourceProvider;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.test.spi.enricher.resource.ResourceProvider;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(WebTest.WebTestExtension.class)
public @interface WebTest {

    @Retention(RUNTIME)
    @Target({ ElementType.FIELD, ElementType.PARAMETER })
    @interface Resource {
    }

    @Retention(RUNTIME)
    @Target(ElementType.METHOD)
    @interface Deployment {
    }

    class WebTestExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

        @ArquillianResource
        private static final Object DUMMY = null;
        private static final ArquillianResource ARQUILLIAN_RESOURCE;

        static {
            try {
                ARQUILLIAN_RESOURCE = WebTestExtension.class.getDeclaredField("DUMMY").getAnnotation(ArquillianResource.class);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }

        private List<ResourceProvider> providers;

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            Class<?> testClass = context.getRequiredTestClass();
            List<Method> methods = Stream.of(testClass.getMethods())
                    .filter(m -> m.getAnnotation(Deployment.class) != null)
                    .collect(Collectors.toList());
            if (methods.size() != 1) {
                throw new IllegalStateException("Expecting a single method annotated with @Deployment");
            }
            Method method = methods.iterator().next();
            method.setAccessible(true);
            Object value = method.invoke(null);
            if (!(value instanceof Archive)) {
                throw new IllegalStateException("Method " + method.getName() + " returned an object which is not an Archive");
            }
            ExtensionContext.Store store = getStore(context);

            JettyEmbeddedConfiguration config = new JettyEmbeddedConfiguration();
            config.setBindHttpPort(0);
            JettyEmbeddedContainer container = new JettyEmbeddedContainer();
            inject(container, "webAppContextProducer", new Instance<>(store, App.class));
            inject(container, "servletContextInstanceProducer", new Instance<>(store, ServletContext.class));

            container.setup(config);
            container.start();
            ProtocolMetaData metaData = container.deploy((Archive) value);
            new Instance<>(store, ProtocolMetaData.class).set(metaData);
            new Instance<>(store, JettyEmbeddedContainer.class).set(container);

            URLResourceProvider url = new URLResourceProvider();
            inject(url, "protocolMetadata", new Instance<>(getStore(context), ProtocolMetaData.class));
            URIResourceProvider uri = new URIResourceProvider();
            inject(uri, "protocolMetadata", new Instance<>(getStore(context), ProtocolMetaData.class));
            providers = Arrays.asList(url, uri);
        }

        private void inject(Object instance, String fieldName, Object value) {
            try {
                Field f = null;
                Class<?> clazz = instance.getClass();
                while (f == null && clazz != null) {
                    try {
                        f = clazz.getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
                if (f == null) {
                    throw new NoSuchFieldException(fieldName);
                }
                inject(instance, f, value);
            } catch (Exception e) {
                throw new RuntimeException("Unable to inject field", e);
            }
        }

        private void inject(Object instance, Field f, Object value) {
            try {
                f.setAccessible(true);
                f.set(instance, value);
            } catch (Exception e) {
                throw new RuntimeException("Unable to inject field", e);
            }
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            Class<?> testClass = context.getRequiredTestClass();
            List<Field> fields = Stream.of(testClass.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(Resource.class))
                    .collect(Collectors.toList());
            if (!fields.isEmpty()) {
                for (Field field : fields) {
                    List<ResourceProvider> matching = providers.stream()
                            .filter(p -> p.canProvide(field.getType()))
                            .collect(Collectors.toList());
                    if (matching.size() == 0) {
                        throw new IllegalStateException("No matching resource provider for " + field.getType());
                    } else if (matching.size() > 1) {
                        throw new IllegalStateException("Ambiguous resource provider for " + field.getType());
                    } else {
                        ResourceProvider rp = matching.iterator().next();
                        Object value = rp.lookup(ARQUILLIAN_RESOURCE);
                        inject(context.getRequiredTestInstance(), field, value);
                    }
                }
            }
        }

        @Override
        public void afterEach(ExtensionContext context) {
        }

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            JettyEmbeddedContainer container
                    = getStore(context).get(JettyEmbeddedContainer.class, JettyEmbeddedContainer.class);
            container.stop();
        }

        private ExtensionContext.Store getStore(ExtensionContext context) {
            return context.getStore(ExtensionContext.Namespace.create(WebTestExtension.class));
        }

        private static class Instance<T> implements InstanceProducer<T> {
            final ExtensionContext.Store store;
            final Class<T> type;

            public Instance(ExtensionContext.Store store, Class<T> type) {
                this.store = store;
                this.type = type;
            }

            public T get() {
                return store.get(type, type);
            }

            public void set(T value) {
                store.put(type, value);
            }
        }

    }
}
