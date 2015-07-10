/**
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
package org.apache.camel.test.blueprint;

import java.io.File;
import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.KeyValueHolder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.container.BlueprintEvent;
import org.osgi.service.blueprint.container.BlueprintListener;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;

/**
 * Base class for OSGi Blueprint unit tests with Camel.
 */
public abstract class CamelBlueprintTestSupport extends CamelTestSupport {
    /** Name of a system property that sets camel context creation timeout. */
    public static final String SPROP_CAMEL_CONTEXT_CREATION_TIMEOUT = "org.apache.camel.test.blueprint.camelContextCreationTimeout";

    private static ThreadLocal<BundleContext> threadLocalBundleContext = new ThreadLocal<BundleContext>();
    private volatile BundleContext bundleContext;
    private final Set<ServiceRegistration<?>> services = new LinkedHashSet<ServiceRegistration<?>>();
    
    /**
     * Override this method if you don't want CamelBlueprintTestSupport create the test bundle
     * @return includeTestBundle
     * If the return value is true CamelBlueprintTestSupport creates the test bundle which includes blueprint configuration files
     * If the return value is false CamelBlueprintTestSupport won't create the test bundle
     * 
     */
    protected boolean includeTestBundle() {
        return true;
    }

   
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected BundleContext createBundleContext() throws Exception {
        final String symbolicName = getClass().getSimpleName();
        final BundleContext answer = CamelBlueprintHelper.createBundleContext(symbolicName, getBlueprintDescriptor(),
            includeTestBundle(), getBundleFilter(), getBundleVersion(), getBundleDirectives());

        // must register override properties early in OSGi containers
        Properties extra = useOverridePropertiesWithPropertiesComponent();
        if (extra != null) {
            answer.registerService(PropertiesComponent.OVERRIDE_PROPERTIES, extra, null);
        }

        Map<String, KeyValueHolder<Object, Dictionary>> map = new LinkedHashMap<String, KeyValueHolder<Object, Dictionary>>();
        addServicesOnStartup(map);

        List<KeyValueHolder<String, KeyValueHolder<Object, Dictionary>>> servicesList = new LinkedList<KeyValueHolder<String, KeyValueHolder<Object, Dictionary>>>();
        for (Map.Entry<String, KeyValueHolder<Object, Dictionary>> entry : map.entrySet()) {
            servicesList.add(asKeyValueService(entry.getKey(), entry.getValue().getKey(), entry.getValue().getValue()));
        }

        addServicesOnStartup(servicesList);

        for (KeyValueHolder<String, KeyValueHolder<Object, Dictionary>> item : servicesList) {
            String clazz = item.getKey();
            Object service = item.getValue().getKey();
            Dictionary dict = item.getValue().getValue();
            log.debug("Registering service {} -> {}", clazz, service);
            ServiceRegistration<?> reg = answer.registerService(clazz, service, dict);
            if (reg != null) {
                services.add(reg);
            }
        }

        // must reuse props as we can do both load from .cfg file and override afterwards
        Dictionary props = new Properties();

        // load configuration file
        String[] file = loadConfigAdminConfigurationFile();
        if (file != null && file.length != 2) {
            throw new IllegalArgumentException("The returned String[] from loadConfigAdminConfigurationFile must be of length 2, was " + file.length);
        }

        if (file != null) {
            if (!new File(file[0]).exists()) {
                throw new IllegalArgumentException("The provided file \"" + file[0] + "\" from loadConfigAdminConfigurationFile doesn't exist");
            }
            CamelBlueprintHelper.setPersistentFileForConfigAdmin(answer, file[1], file[0], props);
        }

        // allow end user to override properties
        String pid = useOverridePropertiesWithConfigAdmin(props);
        if (pid != null) {
            // we will update the configuration now. As OSGi is highly asynchronous, we need to make the tests as repeatable as possible
            // the problem is when blueprint container defines cm:property-placeholder with update-strategy="reload"
            // updating the configuration leads to (felix framework + aries blueprint):
            // 1. schedule org.apache.felix.cm.impl.ConfigurationManager.UpdateConfiguration object to run in config admin thread
            // 2. this thread calls org.apache.felix.cm.impl.ConfigurationImpl#tryBindLocation()
            // 3. org.osgi.service.cm.ConfigurationEvent#CM_LOCATION_CHANGED is send
            // 4. org.apache.aries.blueprint.compendium.cm.ManagedObjectManager.ConfigurationWatcher#updated() is invoked
            // 5. new Thread().start() is called
            // 6. org.apache.aries.blueprint.compendium.cm.ManagedObject#updated() is called
            // 7. org.apache.aries.blueprint.compendium.cm.CmPropertyPlaceholder#updated() is called
            // 8. new Thread().start() is called
            // 9. org.apache.aries.blueprint.services.ExtendedBlueprintContainer#reload() is called which destroys everything in BP container
            // 10. finally reload of BP container is scheduled (in yet another thread)
            //
            // if we start/use camel context between point 9 and 10 we may get many different errors described in https://issues.apache.org/jira/browse/ARIES-961

            // to synchronize this (main) thread of execution with the asynchronous series of events, we can register the following listener.
            // this way be sure that we got to point 3
            final CountDownLatch latch = new CountDownLatch(2);
            answer.registerService(ConfigurationListener.class, new ConfigurationListener() {
                @Override
                public void configurationEvent(ConfigurationEvent event) {
                    if (event.getType() == ConfigurationEvent.CM_LOCATION_CHANGED) {
                        latch.countDown();
                    }
                    // when we update the configuration, BP container will be reloaded as well
                    // hoping that we get the event after *second* restart, let's register the listener
                    answer.registerService(BlueprintListener.class, new BlueprintListener() {
                        @Override
                        public void blueprintEvent(BlueprintEvent event) {
                            if (event.getType() == BlueprintEvent.CREATED && event.getBundle().getSymbolicName().equals(symbolicName)) {
                                latch.countDown();
                            }
                        }
                    }, null);
                }
            }, null);

            ConfigurationAdmin configAdmin = CamelBlueprintHelper.getOsgiService(answer, ConfigurationAdmin.class);
            // passing null as second argument ties the configuration to correct bundle.
            // using single-arg method causes:
            // *ERROR* Cannot use configuration xxx.properties for [org.osgi.service.cm.ManagedService, id=N, bundle=N/jar:file:xyz.jar!/]: No visibility to configuration bound to file:pojosr
            Configuration config = configAdmin.getConfiguration(pid, null);
            if (config == null) {
                throw new IllegalArgumentException("Cannot find configuration with pid " + pid + " in OSGi ConfigurationAdmin service.");
            }
            log.info("Updating ConfigAdmin {} by overriding properties {}", config, props);
            config.update(props);

            latch.await(CamelBlueprintHelper.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        } else {
            // let's wait for BP container to start
            final CountDownLatch latch = new CountDownLatch(1);
            answer.registerService(BlueprintListener.class, new BlueprintListener() {
                @Override
                public void blueprintEvent(BlueprintEvent event) {
                    if (event.getType() == BlueprintEvent.CREATED && event.getBundle().getSymbolicName().equals(symbolicName)) {
                        latch.countDown();
                    }
                }
            }, null);

            latch.await(CamelBlueprintHelper.DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        }

        return answer;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        System.setProperty("skipStartingCamelContext", "true");
        System.setProperty("registerBlueprintCamelContextEager", "true");

        String symbolicName = getClass().getSimpleName();
        if (isCreateCamelContextPerClass()) {
            // test is per class, so only setup once (the first time)
            boolean first = threadLocalBundleContext.get() == null;
            if (first) {
                threadLocalBundleContext.set(createBundleContext());
            }
            bundleContext = threadLocalBundleContext.get();
        } else {
            bundleContext = createBundleContext();
        }

        super.setUp();

        // we don't have to wait for BP container's OSGi service - we've already waited
        // for BlueprintEvent.CREATED

        // start context when we are ready
        log.debug("Staring CamelContext: {}", context.getName());
        context.start();
    }

    /**
     * Override this method to add services to be registered on startup.
     * <p/>
     * You can use the builder methods {@link #asService(Object, java.util.Dictionary)}, {@link #asService(Object, String, String)}
     * to make it easy to add the services to the map.
     */
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        // noop
    }

    /**
     * Override this method to add services to be registered on startup.
     * <p/>
     * You can use the builder methods {@link #asKeyValueService(String, Object, Dictionary)}
     * to make it easy to add the services to the List.
     */
    protected void addServicesOnStartup(List<KeyValueHolder<String, KeyValueHolder<Object, Dictionary>>> services) {
        // noop
    }

    /**
     * Creates a holder for the given service, which make it easier to use {@link #addServicesOnStartup(java.util.Map)}
     */
    protected KeyValueHolder<Object, Dictionary> asService(Object service, Dictionary dict) {
        return new KeyValueHolder<Object, Dictionary>(service, dict);
    }

    /**
     * Creates a holder for the given service, which make it easier to use {@link #addServicesOnStartup(java.util.List)}
     */
    protected KeyValueHolder<String, KeyValueHolder<Object, Dictionary>> asKeyValueService(String name, Object service, Dictionary dict) {
        return new KeyValueHolder<String, KeyValueHolder<Object, Dictionary>>(name, new KeyValueHolder<Object, Dictionary>(service, dict));
    }


    /**
     * Creates a holder for the given service, which make it easier to use {@link #addServicesOnStartup(java.util.Map)}
     */
    protected KeyValueHolder<Object, Dictionary> asService(Object service, String key, String value) {
        Properties prop = new Properties();
        if (key != null && value != null) {
            prop.put(key, value);
        }
        return new KeyValueHolder<Object, Dictionary>(service, prop);
    }

    /**
     * Override this method to override config admin properties.
     *
     * @param props properties where you add the properties to override
     * @return the PID of the OSGi {@link ConfigurationAdmin} which are defined in the Blueprint XML file.
     */
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        return null;
    }

    /**
     * Override this method and provide the name of the .cfg configuration file to use for
     * Blueprint ConfigAdmin service.
     *
     * @return the name of the path for the .cfg file to load, and the persistence-id of the property placeholder.
     */
    protected String[] loadConfigAdminConfigurationFile() {
        return null;
    }

    @After
    @Override
    public void tearDown() throws Exception {
        System.clearProperty("skipStartingCamelContext");
        System.clearProperty("registerBlueprintCamelContextEager");
        super.tearDown();
        if (isCreateCamelContextPerClass()) {
            // we tear down in after class
            return;
        }

        // unregister services
        if (bundleContext != null) {
            for (ServiceRegistration<?> reg : services) {
                bundleContext.ungetService(reg.getReference());
            }
        }
        CamelBlueprintHelper.disposeBundleContext(bundleContext);
    }
    
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if (threadLocalBundleContext.get() != null) {
            CamelBlueprintHelper.disposeBundleContext(threadLocalBundleContext.get());
            threadLocalBundleContext.remove();
        }
        CamelTestSupport.tearDownAfterClass();
    }

    /**
     * Return the system bundle context
     */
    protected BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Gets the bundle descriptor from the classpath.
     * <p/>
     * Return the location(s) of the bundle descriptors from the classpath.
     * Separate multiple locations by comma, or return a single location.
     * <p/>
     * For example override this method and return <tt>OSGI-INF/blueprint/camel-context.xml</tt>
     *
     * @return the location of the bundle descriptor file.
     */
    protected String getBlueprintDescriptor() {
        return null;
    }

    /**
     * Gets filter expression of bundle descriptors.
     * Modify this method if you wish to change default behavior.
     *
     * @return filter expression for OSGi bundles.
     */
    protected String getBundleFilter() {
        return CamelBlueprintHelper.BUNDLE_FILTER;
    }

    /**
     * Gets test bundle version.
     * Modify this method if you wish to change default behavior.
     *
     * @return test bundle version
     */
    protected String getBundleVersion() {
        return CamelBlueprintHelper.BUNDLE_VERSION;
    }

    /**
     * Gets the bundle directives.
     * <p/>
     * Modify this method if you wish to add some directives.
     */
    protected String getBundleDirectives() {
        return null;
    }
    
    /**
     * Returns how long to wait for Camel Context
     * to be created.
     * 
     * @return timeout in milliseconds.
     */
    protected Long getCamelContextCreationTimeout() {
        String tm = System.getProperty(SPROP_CAMEL_CONTEXT_CREATION_TIMEOUT);
        if (tm == null) {
            return null;
        }
        try {
            Long val = Long.valueOf(tm);
            if (val < 0) {
                throw new IllegalArgumentException("Value of " 
                        + SPROP_CAMEL_CONTEXT_CREATION_TIMEOUT
                        + " cannot be negative.");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value of " 
                    + SPROP_CAMEL_CONTEXT_CREATION_TIMEOUT
                    + " has wrong format.", e);
        }
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = null;
        Long timeout = getCamelContextCreationTimeout();
        if (timeout == null) {
            answer = CamelBlueprintHelper.getOsgiService(bundleContext, CamelContext.class);
        } else if (timeout >= 0) {
            answer = CamelBlueprintHelper.getOsgiService(bundleContext, CamelContext.class, timeout);
        } else {
            throw new IllegalArgumentException("getCamelContextCreationTimeout cannot return a negative value.");
        }
        // must override context so we use the correct one in testing
        context = (ModelCamelContext) answer;
        return answer;
    }
   

    protected <T> T getOsgiService(Class<T> type) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type);
    }

    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, timeout);
    }

    protected <T> T getOsgiService(Class<T> type, String filter) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, filter);
    }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        return CamelBlueprintHelper.getOsgiService(bundleContext, type, filter, timeout);
    }

}


