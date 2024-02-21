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
package org.apache.camel.test.spring.junit5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.ExcludingPackageScanClassResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base test-class for classic Spring application such as standalone, web applications. Do <tt>not</tt> use this class
 * for Spring Boot testing, instead use <code>@CamelSpringBootTest</code>.
 */
public abstract class CamelSpringTestSupport extends CamelTestSupport {

    public static final String TEST_CLASS_NAME_PROPERTY = "testClassName";
    public static final String TEST_CLASS_SIMPLE_NAME_PROPERTY = "testClassSimpleName";
    public static final String TEST_DIRECTORY_PROPERTY = "testDirectory";

    protected static final ThreadLocal<AbstractApplicationContext> THREAD_APP_CONTEXT = new ThreadLocal<>();
    protected static final Object LOCK = new Object();

    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringTestSupport.class);

    protected AbstractApplicationContext applicationContext;

    protected abstract AbstractApplicationContext createApplicationContext();

    @Override
    public void postProcessTest() throws Exception {
        if (isCreateCamelContextPerClass()) {
            applicationContext = THREAD_APP_CONTEXT.get();
        }
        super.postProcessTest();
    }

    @Override
    public void doPreSetup() throws Exception {
        if (!"true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"))) {
            // tell camel-spring it should not trigger starting CamelContext, since we do that later
            // after we are finished setting up the unit test
            synchronized (LOCK) {
                SpringCamelContext.setNoStart(true);
                if (isCreateCamelContextPerClass()) {
                    applicationContext = THREAD_APP_CONTEXT.get();
                    if (applicationContext == null) {
                        applicationContext = doCreateApplicationContext();
                        THREAD_APP_CONTEXT.set(applicationContext);
                    }
                } else {
                    applicationContext = doCreateApplicationContext();
                }
                SpringCamelContext.setNoStart(false);
            }
        } else {
            LOG.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
        }
    }

    private AbstractApplicationContext doCreateApplicationContext() {
        AbstractApplicationContext context = createApplicationContext();
        assertNotNull(context, "Should have created a valid Spring application context");

        String[] profiles = activeProfiles();
        if (profiles != null && profiles.length > 0) {
            // the context must not be active
            if (context.isActive()) {
                throw new IllegalStateException(
                        "Cannot active profiles: " + Arrays.asList(profiles) + " on active Spring application context: "
                                                + context
                                                + ". The code in your createApplicationContext() method should be adjusted to create the application context with refresh = false as parameter");
            }
            LOG.info("Spring activating profiles: {}", Arrays.asList(profiles));
            context.getEnvironment().setActiveProfiles(profiles);
        }

        // ensure the context has been refreshed at least once
        if (!context.isActive()) {
            context.refresh();
        }

        return context;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();

        if (!isCreateCamelContextPerClass()) {
            IOHelper.close(applicationContext);
            applicationContext = null;
        }
    }

    @Override
    public void doPostTearDown() throws Exception {
        super.doPostTearDown();

        if (THREAD_APP_CONTEXT.get() != null) {
            IOHelper.close(THREAD_APP_CONTEXT.get());
            THREAD_APP_CONTEXT.remove();
        }
    }

    /**
     * Create a parent context that initializes a {@link org.apache.camel.spi.PackageScanClassResolver} to exclude a set
     * of given classes from being resolved. Typically this is used at test time to exclude certain routes, which might
     * otherwise be just noisy, from being discovered and initialized.
     * <p/>
     * To use this filtering mechanism it is necessary to provide the
     * {@link org.springframework.context.ApplicationContext} returned from here as the parent context to your test
     * context e.g.
     *
     * <pre>
     * protected AbstractXmlApplicationContext createApplicationContext() {
     *     return new ClassPathXmlApplicationContext(
     *             new String[] { &quot;test-context.xml&quot; }, getRouteExcludingApplicationContext());
     * }
     * </pre>
     *
     * This will, in turn, call the template methods <code>excludedRoutes</code> and <code>excludedRoute</code> to
     * determine the classes to be excluded from scanning.
     *
     * @return ApplicationContext a parent {@link org.springframework.context.ApplicationContext} configured to exclude
     *         certain classes from package scanning
     */
    protected ApplicationContext getRouteExcludingApplicationContext() {
        GenericApplicationContext routeExcludingContext = new GenericApplicationContext();
        routeExcludingContext.registerBeanDefinition("excludingResolver",
                new RootBeanDefinition(ExcludingPackageScanClassResolver.class));
        routeExcludingContext.refresh();

        ExcludingPackageScanClassResolver excludingResolver
                = routeExcludingContext.getBean("excludingResolver", ExcludingPackageScanClassResolver.class);
        List<Class<?>> excluded = Arrays.asList(excludeRoutes());
        excludingResolver.setExcludedClasses(new HashSet<>(excluded));

        return routeExcludingContext;
    }

    /**
     * Template method used to exclude {@link org.apache.camel.Route} from the test time context route scanning
     *
     * @return Class[] the classes to be excluded from test time context route scanning
     */
    protected Class<?>[] excludeRoutes() {
        Class<?> excludedRoute = excludeRoute();
        return excludedRoute != null ? new Class[] { excludedRoute } : new Class[0];
    }

    /**
     * Template method used to exclude a {@link org.apache.camel.Route} from the test camel context
     */
    protected Class<?> excludeRoute() {
        return null;
    }

    /**
     * Looks up the mandatory spring bean of the given name and type, failing if it is not present or the correct type
     */
    public <T> T getMandatoryBean(Class<T> type, String name) {
        Object value = applicationContext.getBean(name);
        assertNotNull(value, "No spring bean found for name <" + name + ">");
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            fail("Spring bean <" + name + "> is not an instanceof " + type.getName() + " but is of type "
                 + ObjectHelper.className(value));
            return null;
        }
    }

    /**
     * Which active profiles should be used.
     * <p/>
     * <b>Important:</b> When using active profiles, then the code in {@link #createApplicationContext()} should create
     * the Spring {@link org.springframework.context.support.AbstractApplicationContext} without refreshing. For example
     * creating an {@link org.springframework.context.support.ClassPathXmlApplicationContext} you would need to pass in
     * <tt>false</tt> in the refresh parameter, in the constructor. Camel will thrown an {@link IllegalStateException}
     * if this is not correct stating this problem. The reason is that we cannot active profiles <b>after</b> a Spring
     * application context has already been refreshed, and is active.
     *
     * @return an array of active profiles to use, use <tt>null</tt> to not use any active profiles.
     */
    protected String[] activeProfiles() {
        return null;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // don't start the springCamelContext if we
        return SpringCamelContext.springCamelContext(applicationContext, false);
    }

    public AbstractXmlApplicationContext newAppContext(String configLocation) throws BeansException {
        return newAppContext(configLocation, getClass(), getTranslationProperties());
    }

    public AbstractXmlApplicationContext newAppContext(String... configLocations) throws BeansException {
        return newAppContext(configLocations, getClass(), getTranslationProperties());
    }

    protected Map<String, String> getTranslationProperties() {
        return getTranslationProperties(getClass());
    }

    static Map<String, String> getTranslationProperties(Class<?> testClass) {
        Map<String, String> props = new HashMap<>();
        props.put(TEST_CLASS_NAME_PROPERTY, testClass.getName());
        props.put(TEST_CLASS_SIMPLE_NAME_PROPERTY, testClass.getSimpleName());

        Path testDir = Paths.get("target", "data", testClass.getSimpleName());
        props.put(TEST_DIRECTORY_PROPERTY, testDir.toString());
        return props;
    }

    public static AbstractXmlApplicationContext newAppContext(String configLocation, Class<?> clazz) {
        Map<String, String> props = getTranslationProperties(clazz);
        return newAppContext(configLocation, clazz, props);
    }

    public static MyXmlApplicationContext newAppContext(String configLocation, Class<?> clazz, Map<String, String> props) {
        return new MyXmlApplicationContext(configLocation, clazz, props);
    }

    public static MyXmlApplicationContext newAppContext(String[] configLocations, Class<?> clazz, Map<String, String> props) {
        return new MyXmlApplicationContext(configLocations, clazz, props);
    }

    public static class MyXmlApplicationContext extends AbstractXmlApplicationContext {
        private final Resource[] configResources;

        public MyXmlApplicationContext(String configLocation, Class<?> clazz, Map<String, String> properties) {
            this(new String[] { configLocation }, clazz, properties);
        }

        public MyXmlApplicationContext(String[] configLocations, Class<?> clazz, Map<String, String> properties) {
            super(null);
            configResources = Stream.of(configLocations)
                    .map(loc -> new TranslatedResource(new ClassPathResource(loc, clazz), properties))
                    .toArray(Resource[]::new);
            refresh();
        }

        @Override
        protected Resource[] getConfigResources() {
            return configResources;
        }
    }

    public static class TranslatedResource extends AbstractResource {

        private final Resource delegate;
        private final Map<String, String> properties;

        public TranslatedResource(Resource delegate, Map<String, String> properties) {
            this.delegate = delegate;
            this.properties = properties;
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (properties.size() > 0) {
                StringWriter sw = new StringWriter();
                try (InputStreamReader r = new InputStreamReader(delegate.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buf = new char[32768];
                    int l;
                    while ((l = r.read(buf)) > 0) {
                        sw.write(buf, 0, l);
                    }
                }
                String before = sw.toString();
                String p = properties.keySet().stream().map(Pattern::quote)
                        .collect(Collectors.joining("|", Pattern.quote("{{") + "(", ")" + Pattern.quote("}}")));
                Matcher m = Pattern.compile(p).matcher(before);
                StringBuilder sb = new StringBuilder(before.length());
                while (m.find()) {
                    m.appendReplacement(sb, properties.get(m.group(1)));
                }
                m.appendTail(sb);
                String after = sb.toString();
                return new ByteArrayInputStream(after.getBytes(StandardCharsets.UTF_8));
            } else {
                return delegate.getInputStream();
            }
        }
    }
}
