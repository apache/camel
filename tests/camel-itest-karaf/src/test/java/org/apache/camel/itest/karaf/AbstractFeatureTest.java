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
package org.apache.camel.itest.karaf;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.osgi.CamelContextFactory;

import org.junit.After;
import org.junit.Before;

import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.vmOption;


public abstract class AbstractFeatureTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFeatureTest.class);

    @Inject
    protected BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
        LOG.info("Calling the setUp method ");
        LOG.info("The BundleContext is " + bundleContext);
        Thread.sleep(3000);
    }

    @After
    public void tearDown() throws Exception {
        LOG.info("Calling the tearDown method ");
    }

    protected void testComponent(String component) throws Exception {
        long max = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                assertNotNull("Cannot get component with name: " + component, createCamelContext().getComponent(component));
                return;
            } catch (Exception t) {
                if (System.currentTimeMillis() < max) {
                    Thread.sleep(1000);
                } else {
                    throw t;
                }
            }
        }
    }

    protected void testComponent() throws Exception {
        testComponent(extractName(getClass()));
    }

    protected void testDataFormat(String format) throws Exception {
        long max = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                DataFormatDefinition dataFormatDefinition = createDataformatDefinition(format);                
                assertNotNull(dataFormatDefinition);
                assertNotNull(dataFormatDefinition.getDataFormat(new DefaultRouteContext(createCamelContext())));
                return;
            } catch (Exception t) {
                if (System.currentTimeMillis() < max) {
                    Thread.sleep(1000);
                } else {
                    throw t;
                }
            }
        }
    }

    protected DataFormatDefinition createDataformatDefinition(String format) {
        return null;
    }

    protected void testLanguage(String lang) throws Exception {
        long max = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                assertNotNull(createCamelContext().resolveLanguage(lang));
                return;
            } catch (Exception t) {
                if (System.currentTimeMillis() < max) {
                    Thread.sleep(1000);
                } else {
                    throw t;
                }
            }
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        LOG.info("Creating the CamelContext ...");
        setThreadContextClassLoader();
        CamelContextFactory factory = new CamelContextFactory();
        factory.setBundleContext(bundleContext);
        LOG.info("Get the bundleContext is " + bundleContext);
        return factory.createContext();
    }

    protected void setThreadContextClassLoader() {
        // set the thread context classloader current bundle classloader
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
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
                versionAsInProject().type("xml/features");
    }
    
    public static UrlReference getKarafFeatureUrl() {
        String karafVersion = System.getProperty("karafVersion");
        LOG.info("*** The karaf version is " + karafVersion + " ***");

        String type = "xml/features";
        return mavenBundle().groupId("org.apache.karaf.assemblies.features").
            artifactId("standard").version(karafVersion).type(type);
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
            //
        }
        String karafVersion = p.getProperty("org.apache.karaf/apache-karaf/version");
        if (karafVersion == null) {
            karafVersion = System.getProperty("karafVersion");
        }
        if (karafVersion == null) {
            // setup the default version of it
            karafVersion = "2.4.0";
        }
        return karafVersion;
    }
    public static MavenArtifactProvisionOption getJUnitBundle() {
        MavenArtifactProvisionOption mavenOption = mavenBundle().groupId("org.apache.servicemix.bundles")
            .artifactId("org.apache.servicemix.bundles.junit");
        mavenOption.versionAsInProject().start(true).startLevel(10);
        return mavenOption;
    }

    public static Option[] configure(String mainFeature, String... extraFeatures) {
        switchPlatformEncodingToUTF8();
        String karafVersion = getKarafVersion();
        LOG.info("*** The karaf version is " + karafVersion + " ***");

        List<String> list = new ArrayList<String>();
        list.add("cxf-jaxb");
        list.add("camel-core");
        list.add("camel-spring");
        list.add("camel-" + mainFeature);
        for (String extra : extraFeatures) {
            list.add("camel-" + extra);
        }
        String[] features = list.toArray(new String[list.size()]);

        Option[] options = new Option[] {
            // for remote debugging
            //org.ops4j.pax.exam.CoreOptions.vmOption("-Xdebug"),
            //org.ops4j.pax.exam.CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"),
            
            KarafDistributionOption.karafDistributionConfiguration()
                    .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("tar.gz").versionAsInProject())
                    .karafVersion(karafVersion)
                    .name("Apache Karaf")
                    .useDeployFolder(false).unpackDirectory(new File("target/paxexam/unpack/")),

            vmOption("-Dfile.encoding=UTF-8"),

            //KarafDistributionOption.keepRuntimeFolder(),
            // override the config.properties (to fix pax-exam bug)
            //KarafDistributionOption.replaceConfigurationFile("etc/config.properties", new File("src/test/resources/org/apache/camel/itest/karaf/config.properties")),
            // Update the jre.properties to export the sun.misc package
            KarafDistributionOption.replaceConfigurationFile("etc/jre.properties", new File("src/test/resources/org/apache/camel/itest/karaf/jre.properties")),
            KarafDistributionOption.replaceConfigurationFile("etc/custom.properties", new File("src/test/resources/org/apache/camel/itest/karaf/custom.properties")),
            KarafDistributionOption.replaceConfigurationFile("etc/org.ops4j.pax.url.mvn.cfg", new File("src/test/resources/org/apache/camel/itest/karaf/org.ops4j.pax.url.mvn.cfg")),
            
            getJUnitBundle(),

            // we need INFO logging otherwise we cannot see what happens
            new LogLevelOption(LogLevelOption.LogLevel.INFO),


            // install the cxf jaxb spec as the karaf doesn't provide it by default
            KarafDistributionOption.features(getCamelKarafFeatureUrl(), features)
        };

        return options;
    }

    protected Option[] configureComponent() {
        return configure(extractName(getClass()));
    }

}
