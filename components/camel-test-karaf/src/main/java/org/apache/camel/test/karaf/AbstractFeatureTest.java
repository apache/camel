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
package org.apache.camel.test.karaf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.karaf.features.FeaturesService;
import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

public abstract class AbstractFeatureTest {

    public static final Long SERVICE_TIMEOUT = 30000L;
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractFeatureTest.class);

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected BlueprintContainer blueprintContainer;

    @Inject
    protected FeaturesService featuresService;

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
        // makes sure the generated Test-Bundle contains this import!
        probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "*");
        return probe;
    }

    @Before
    public void setUp() throws Exception {
        LOG.info("setUp() using BundleContext: {}", bundleContext);
    }

    @After
    public void tearDown() throws Exception {
        LOG.info("tearDown()");
    }

    protected Bundle installBlueprintAsBundle(String name, URL url, boolean start) throws BundleException {
        return installBlueprintAsBundle(name, url, start, bundle -> { });
    }

    protected Bundle installBlueprintAsBundle(String name, URL url, boolean start, Consumer<Object> consumer) throws BundleException {
        // TODO Type Consumer<TinyBundle> cannot be used for this method signature to avoid bundle dependency to pax tinybundles
        TinyBundle bundle = TinyBundles.bundle();
        bundle.add("OSGI-INF/blueprint/blueprint-" + name.toLowerCase(Locale.ENGLISH) + ".xml", url);
        bundle.set("Manifest-Version", "2")
                .set("Bundle-ManifestVersion", "2")
                .set("Bundle-SymbolicName", name)
                .set("Bundle-Version", "1.0.0");
        consumer.accept(bundle);
        Bundle answer = bundleContext.installBundle(name, bundle.build());

        if (start) {
            answer.start();
        }
        return answer;
    }

    protected Bundle installSpringAsBundle(String name, URL url, boolean start) throws BundleException {
        return installSpringAsBundle(name, url, start, bundle -> { });
    }

    protected Bundle installSpringAsBundle(String name, URL url, boolean start, Consumer<Object> consumer) throws BundleException {
        // TODO Type Consumer<TinyBundle> cannot be used for this method signature to avoid bundle dependency to pax tinybundles
        TinyBundle bundle = TinyBundles.bundle();
        bundle.add("META-INF/spring/spring-" + name.toLowerCase(Locale.ENGLISH) + ".xml", url);
        bundle.set("Manifest-Version", "2")
                .set("Bundle-ManifestVersion", "2")
                .set("Bundle-SymbolicName", name)
                .set("Bundle-Version", "1.0.0");
        consumer.accept(bundle);
        Bundle answer = bundleContext.installBundle(name, bundle.build());

        if (start) {
            answer.start();
        }
        return answer;
    }

    protected void installCamelFeature(String mainFeature) throws Exception {
        if (!mainFeature.startsWith("camel-")) {
            mainFeature = "camel-" + mainFeature;
        }
        LOG.info("Install main feature: {}", mainFeature);
        // do not refresh bundles causing out bundle context to be invalid
        // TODO: see if we can find a way maybe to install camel.xml as bundle/feature instead of part of unit test (see src/test/resources/OSGI-INF/blueprint)
        featuresService.installFeature(mainFeature, EnumSet.of(FeaturesService.Option.NoAutoRefreshBundles));
    }

    protected void overridePropertiesWithConfigAdmin(String pid, Properties props) throws IOException {
        ConfigurationAdmin configAdmin = getOsgiService(bundleContext, ConfigurationAdmin.class);
        // passing null as second argument ties the configuration to correct bundle.
        Configuration config = configAdmin.getConfiguration(pid, null);
        if (config == null) {
            throw new IllegalArgumentException("Cannot find configuration with pid " + pid + " in OSGi ConfigurationAdmin service.");
        }

        // let's merge configurations
        Dictionary<String, Object> currentProperties = config.getProperties();
        Dictionary newProps = new Properties();
        if (currentProperties == null) {
            currentProperties = newProps;
        }
        for (Enumeration<String> ek = currentProperties.keys(); ek.hasMoreElements();) {
            String k = ek.nextElement();
            newProps.put(k, currentProperties.get(k));
        }
        for (String p : props.stringPropertyNames()) {
            newProps.put(p, props.getProperty(p));
        }

        LOG.info("Updating ConfigAdmin {} by overriding properties {}", config, newProps);
        config.update(newProps);
    }

    protected void testComponent(String component) throws Exception {
        testComponent("camel-" + component, component);
    }

    protected void testComponent(String mainFeature, String component) throws Exception {
        LOG.info("Looking up CamelContext(myCamel) in OSGi Service Registry");

        installCamelFeature(mainFeature);

        CamelContext camelContext = getOsgiService(bundleContext, CamelContext.class, "(camel.context.name=myCamel)", SERVICE_TIMEOUT);
        assertNotNull("Cannot find CamelContext with name myCamel", camelContext);

        LOG.info("Getting Camel component: {}", component);
        // do not auto start the component as it may not have been configured properly and fail in its start method
        Component comp = camelContext.getComponent(component, true, false);
        assertNotNull("Cannot get component with name: " + component, comp);

        LOG.info("Found Camel component: {} instance: {} with className: {}", component, comp, comp.getClass());
    }

    protected void testDataFormat(String dataFormat) throws Exception {
        testDataFormat("camel-" + dataFormat, dataFormat);
    }

    protected void testDataFormat(String mainFeature, String dataFormat) throws Exception {
        LOG.info("Looking up CamelContext(myCamel) in OSGi Service Registry");

        installCamelFeature(mainFeature);

        CamelContext camelContext = getOsgiService(bundleContext, CamelContext.class, "(camel.context.name=myCamel)", SERVICE_TIMEOUT);
        assertNotNull("Cannot find CamelContext with name myCamel", camelContext);

        LOG.info("Getting Camel dataformat: {}", dataFormat);
        DataFormat df = camelContext.resolveDataFormat(dataFormat);
        assertNotNull("Cannot get dataformat with name: " + dataFormat, df);

        LOG.info("Found Camel dataformat: {} instance: {} with className: {}", dataFormat, df, df.getClass());
    }

    protected void testLanguage(String language) throws Exception {
        testLanguage("camel-" + language, language);
    }

    protected void testLanguage(String mainFeature, String language) throws Exception {
        LOG.info("Looking up CamelContext(myCamel) in OSGi Service Registry");

        installCamelFeature(mainFeature);

        CamelContext camelContext = getOsgiService(bundleContext, CamelContext.class, "(camel.context.name=myCamel)", 20000);
        assertNotNull("Cannot find CamelContext with name myCamel", camelContext);

        LOG.info("Getting Camel language: {}", language);
        Language lan = camelContext.resolveLanguage(language);
        assertNotNull("Cannot get language with name: " + language, lan);

        LOG.info("Found Camel language: {} instance: {} with className: {}", language, lan, lan.getClass());
    }

    public static String extractName(Class<?> clazz) {
        String name = clazz.getName();
        int id0 = name.indexOf("Camel") + "Camel".length();
        int id1 = name.indexOf("Test");
        StringBuilder sb = new StringBuilder();
        for (int i = id0; i < id1; i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append("-");
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public static UrlReference getCamelKarafFeatureUrl() {
        return mavenBundle().
                groupId("org.apache.camel.karaf").
                artifactId("apache-camel").
                version(getCamelKarafFeatureVersion()).
                type("xml/features");
    }

    private static String getCamelKarafFeatureVersion() {
        String camelKarafFeatureVersion = System.getProperty("camelKarafFeatureVersion");
        if (camelKarafFeatureVersion == null) {
            throw new RuntimeException("Please specify the maven artifact version to use for org.apache.camel.karaf/apache-camel through the camelKarafFeatureVersion System property");
        }
        return camelKarafFeatureVersion;
    }

    private static void switchPlatformEncodingToUTF8() {
        try {
            System.setProperty("file.encoding", "UTF-8");
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getKarafVersion() {
        InputStream ins = AbstractFeatureTest.class.getResourceAsStream("/META-INF/maven/dependencies.properties");
        Properties p = new Properties();
        try {
            p.load(ins);
        } catch (Throwable t) {
            // ignore
        }
        String karafVersion = p.getProperty("org.apache.karaf/apache-karaf/version");
        if (karafVersion == null) {
            karafVersion = System.getProperty("karafVersion");
        }
        if (karafVersion == null) {
            // setup the default version of it
            karafVersion = "4.1.0";
        }
        return karafVersion;
    }

    public static Option[] configure(String... extra) {

        List<String> camel = new ArrayList<>();
        camel.add("camel");
        if (extra != null && extra.length > 0) {
            for (String e : extra) {
                camel.add(e);
            }
        }
        final String[] camelFeatures = camel.toArray(new String[camel.size()]);

        switchPlatformEncodingToUTF8();
        String karafVersion = getKarafVersion();
        LOG.info("*** Apache Karaf version is " + karafVersion + " ***");

        Option[] options = new Option[]{
            // for remote debugging
            //org.ops4j.pax.exam.CoreOptions.vmOption("-Xdebug"),
            //org.ops4j.pax.exam.CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"),

            KarafDistributionOption.karafDistributionConfiguration()
                    .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("tar.gz").versionAsInProject())
                    .karafVersion(karafVersion)
                    .name("Apache Karaf")
                    .useDeployFolder(false).unpackDirectory(new File("target/paxexam/unpack/")),
            logLevel(LogLevelOption.LogLevel.INFO),

            // keep the folder so we can look inside when something fails
            keepRuntimeFolder(),

            // Disable the SSH port
            configureConsole().ignoreRemoteShell(),

            // need to modify the jre.properties to export some com.sun packages that some features rely on
//            KarafDistributionOption.replaceConfigurationFile("etc/jre.properties", new File("src/test/resources/jre.properties")),

            vmOption("-Dfile.encoding=UTF-8"),

            // Disable the Karaf shutdown port
            editConfigurationFilePut("etc/custom.properties", "karaf.shutdown.port", "-1"),

            // Assign unique ports for Karaf
//            editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", Integer.toString(AvailablePortFinder.getNextAvailable())),
//            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", Integer.toString(AvailablePortFinder.getNextAvailable())),
//            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", Integer.toString(AvailablePortFinder.getNextAvailable())),

            // install junit
            CoreOptions.junitBundles(),

            // install camel
            features(getCamelKarafFeatureUrl(), camelFeatures),

            // install camel-test-karaf as bundle (not feature as the feature causes a bundle refresh that invalidates the @Inject bundleContext)
            mavenBundle().groupId("org.apache.camel").artifactId("camel-test-karaf").versionAsInProject()
        };

        return options;
    }

    protected <T> T getOsgiService(BundleContext bundleContext, Class<T> type) {
        return getOsgiService(bundleContext, type, null, SERVICE_TIMEOUT);
    }

    protected <T> T getOsgiService(BundleContext bundleContext, Class<T> type, long timeout) {
        return getOsgiService(bundleContext, type, null, timeout);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOsgiService(BundleContext bundleContext, Class<T> type, String filter, long timeout) {
        ServiceTracker tracker;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = tracker.waitForService(timeout);

            if (svc == null) {
                Dictionary<?, ?> dic = bundleContext.getBundle().getHeaders();
                LOG.warn("Test bundle headers: " + explode(dic));

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    LOG.warn("ServiceReference: " + ref + ", bundle: " + ref.getBundle() + ", symbolicName: " + ref.getBundle().getSymbolicName());
                }

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    LOG.warn("Filtered ServiceReference: " + ref + ", bundle: " + ref.getBundle() + ", symbolicName: " + ref.getBundle().getSymbolicName());
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Explode the dictionary into a <code>,</code> delimited list of <code>key=value</code> pairs.
     */
    private static String explode(Dictionary<?, ?> dictionary) {
        Enumeration<?> keys = dictionary.keys();
        StringBuilder result = new StringBuilder();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            result.append(String.format("%s=%s", key, dictionary.get(key)));
            if (keys.hasMoreElements()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /**
     * Provides an iterable collection of references, even if the original array is <code>null</code>.
     */
    private static Collection<ServiceReference> asCollection(ServiceReference[] references) {
        return references == null ? new ArrayList<ServiceReference>(0) : Arrays.asList(references);
    }

}
