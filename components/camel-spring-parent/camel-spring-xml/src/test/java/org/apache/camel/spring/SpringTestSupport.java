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
package org.apache.camel.spring;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.seda.SedaComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultPackageScanClassResolver;
import org.apache.camel.impl.scan.AssignableToPackageScanFilter;
import org.apache.camel.impl.scan.InvertingPackageScanFilter;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import static org.apache.camel.management.DefaultManagementAgent.DEFAULT_DOMAIN;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_CONTEXT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_NAME;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_TYPE;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_COMPONENT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_CONTEXT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_STEP;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_THREAD_POOL;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class SpringTestSupport extends ContextTestSupport {
    protected AbstractXmlApplicationContext applicationContext;

    protected abstract AbstractXmlApplicationContext createApplicationContext();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        DefaultCamelContext.setDisableJmx(!useJmx());
        Class<?>[] excluded = excludeRoutes();
        if (excluded != null && excluded.length > 0) {
            StringJoiner excludedRoutes = new StringJoiner(",");
            for (Class<?> clazz : excluded) {
                excludedRoutes.add(clazz.getName());
            }
            DefaultCamelContext.setExcludeRoutes(excludedRoutes.toString());
        }

        applicationContext = createApplicationContext();
        assertNotNull(applicationContext, "Should have created a valid spring context");
        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        IOHelper.close(applicationContext);
        DefaultCamelContext.clearOptions();
    }

    private static class ExcludingPackageScanClassResolver extends DefaultPackageScanClassResolver {

        public void setExcludedClasses(Set<Class<?>> excludedClasses) {
            if (excludedClasses == null) {
                excludedClasses = Collections.emptySet();
            }
            addFilter(new InvertingPackageScanFilter(new AssignableToPackageScanFilter(excludedClasses)));
        }
    }

    /**
     * Create a parent context that initializes a {@link org.apache.camel.spi.PackageScanClassResolver} to exclude a set
     * of given classes from being resolved. Typically this is used at test time to exclude certain routes, which might
     * otherwise be just noisy, from being discovered and initialized.
     * <p/>
     * To use this filtering mechanism it is necessary to provide the {@link ApplicationContext} returned from here as
     * the parent context to your test context e.g.
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
     * @see    org.apache.camel.spring.config.scan.SpringComponentScanTest for an example.
     * @return ApplicationContext a parent {@link ApplicationContext} configured to exclude certain classes from package
     *         scanning
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
        T value = applicationContext.getBean(name, type);
        assertNotNull(value, "No spring bean found for name <" + name + ">");
        return value;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = SpringCamelContext.springCamelContext(applicationContext, true);
        // make SEDA run faster
        context.getComponent("seda", SedaComponent.class).setDefaultPollTimeout(10);
        return context;
    }

    public ObjectName getContextObjectName() throws MalformedObjectNameException {
        return getCamelObjectName(TYPE_CONTEXT, context.getName());
    }

    public ObjectName getCamelObjectName(String type, String name) throws MalformedObjectNameException {
        String quote;
        switch (type) {
            case TYPE_CONTEXT:
            case TYPE_COMPONENT:
            case TYPE_ENDPOINT:
            case TYPE_PROCESSOR:
            case TYPE_ROUTE:
            case TYPE_THREAD_POOL:
            case TYPE_STEP:
                quote = "\"";
                break;
            default:
                quote = "";
                break;
        }
        String on = DEFAULT_DOMAIN + ":"
                    + KEY_CONTEXT + "=" + context.getManagementName() + ","
                    + KEY_TYPE + "=" + type + ","
                    + KEY_NAME + "=" + quote + name + quote;
        return ObjectName.getInstance(on);
    }
}
