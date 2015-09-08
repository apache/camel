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
import java.io.IOException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.aries.blueprint.compendium.cm.CmNamespaceHandler;
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
import org.osgi.service.blueprint.container.BlueprintEvent;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

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

    /**
     * <p>Override this method if you want to start Blueprint containers asynchronously using the thread
     * that starts the bundles itself.
     * By default this method returns <code>true</code> which means Blueprint Extender will use thread pool
     * (threads named "<code>Blueprint Extender: N</code>") to startup Blueprint containers.</p>
     * <p>Karaf and Fuse OSGi containers use synchronous startup.</p>
     * <p>Asynchronous startup is more in the <em>spirit</em> of OSGi and usually means that if everything works fine
     * asynchronously, it'll work synchronously as well. This isn't always true otherwise.</p>
     * @return
     */
    protected boolean useAsynchronousBlueprintStartup() {
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected BundleContext createBundleContext() throws Exception {
        System.setProperty("org.apache.aries.blueprint.synchronous", Boolean.toString(!useAsynchronousBlueprintStartup()));

        final String symbolicName = getClass().getSimpleName();
        final BundleContext answer = CamelBlueprintHelper.createBundleContext(symbolicName, getBlueprintDescriptor(),
            includeTestBundle(), getBundleFilter(), getBundleVersion(), getBundleDirectives());

        boolean expectReload = expectBlueprintContainerReloadOnConfigAdminUpdate();

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
        final Dictionary props = new Properties();

        // load configuration file
        String[] file = loadConfigAdminConfigurationFile();
        if (file != null && file.length != 2) {
            throw new IllegalArgumentException("The returned String[] from loadConfigAdminConfigurationFile must be of length 2, was " + file.length);
        }

        // if blueprint XML uses <cm:property-placeholder> (any update-strategy and any default properties)
        // - org.apache.aries.blueprint.compendium.cm.ManagedObjectManager.register() is called
        // - ManagedServiceUpdate is scheduled in felix.cm
        // - org.apache.felix.cm.impl.ConfigurationImpl.setDynamicBundleLocation() is called
        // - CM_LOCATION_CHANGED event is fired
        // - if BP was alredy created, it's <cm:property-placeholder> receives the event and
        // - org.apache.aries.blueprint.compendium.cm.CmPropertyPlaceholder.updated() is called,
        //   but no BP reload occurs
        // we will however wait for BP container of the test bundle to become CREATED for the first time
        // each configadmin update *may* lead to reload of BP container, if it uses <cm:property-placeholder>
        // with update-strategy="reload"

        // we will gather timestamps of BP events. We don't want to be fooled but repeated events related
        // to the same state of BP container
        Set<Long> bpEvents = new HashSet<>();

        CamelBlueprintHelper.waitForBlueprintContainer(bpEvents, answer, symbolicName, BlueprintEvent.CREATED, null);

        if (file != null) {
            if (!new File(file[0]).exists()) {
                throw new IllegalArgumentException("The provided file \"" + file[0] + "\" from loadConfigAdminConfigurationFile doesn't exist");
            }
            CamelBlueprintHelper.setPersistentFileForConfigAdmin(answer, file[1], file[0], props, symbolicName, bpEvents, expectReload);
        }

        // allow end user to override properties
        String pid = useOverridePropertiesWithConfigAdmin(props);
        if (pid != null) {
            // we will update the configuration again
            ConfigurationAdmin configAdmin = CamelBlueprintHelper.getOsgiService(answer, ConfigurationAdmin.class);
            // passing null as second argument ties the configuration to correct bundle.
            // using single-arg method causes:
            // *ERROR* Cannot use configuration xxx.properties for [org.osgi.service.cm.ManagedService, id=N, bundle=N/jar:file:xyz.jar!/]: No visibility to configuration bound to felix-connect
            final Configuration config = configAdmin.getConfiguration(pid, null);
            if (config == null) {
                throw new IllegalArgumentException("Cannot find configuration with pid " + pid + " in OSGi ConfigurationAdmin service.");
            }
            log.info("Updating ConfigAdmin {} by overriding properties {}", config, props);
            if (expectReload) {
                CamelBlueprintHelper.waitForBlueprintContainer(bpEvents, answer, symbolicName, BlueprintEvent.CREATED, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            config.update(props);
                        } catch (IOException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
                });
            } else {
                config.update(props);
            }
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
     * This method may be overriden to instruct BP test support that BP container will reloaded when
     * Config Admin configuration is updated. By default, this is expected, when blueprint XML definition
     * contains <code>&lt;cm:property-placeholder persistent-id="PID" update-strategy="reload"&gt;</code>
     */
    protected boolean expectBlueprintContainerReloadOnConfigAdminUpdate() {
        boolean expectedReload = false;
        String descriptor = getBlueprintDescriptor();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            // cm-1.0 doesn't define update-strategy attribute
            Set<String> cmNamesaces = new HashSet<>(Arrays.asList(
                    CmNamespaceHandler.BLUEPRINT_CM_NAMESPACE_1_1,
                    CmNamespaceHandler.BLUEPRINT_CM_NAMESPACE_1_2,
                    CmNamespaceHandler.BLUEPRINT_CM_NAMESPACE_1_3
            ));
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(getClass().getClassLoader().getResourceAsStream(descriptor));
            NodeList nl = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node node = nl.item(i);
                if (node instanceof Element) {
                    Element pp = (Element) node;
                    if (cmNamesaces.contains(pp.getNamespaceURI())) {
                        String us = pp.getAttribute("update-strategy");
                        if (us != null && us.equals("reload")) {
                            expectedReload = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return expectedReload;
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


