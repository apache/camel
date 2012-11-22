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
package org.apache.camel.itest.osgi;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.osgi.CamelContextFactory;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.openengsb.labs.paxexam.karaf.options.KarafDistributionOption.replaceConfigurationFile;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.scanFeatures;
import static org.ops4j.pax.exam.OptionUtils.combine;

public class OSGiIntegrationTestSupport extends CamelTestSupport {
    protected static final transient Logger LOG = LoggerFactory.getLogger(OSGiIntegrationTestSupport.class);
    protected static final AtomicInteger COUNTER = new AtomicInteger();
    protected static String workDir = "target/paxrunner/";
    @Inject
    protected BundleContext bundleContext;
    
    protected Bundle getInstalledBundle(String symbolicName) {
        for (Bundle b : bundleContext.getBundles()) {
            if (b.getSymbolicName().equals(symbolicName)) {
                return b;
            }
        }
        for (Bundle b : bundleContext.getBundles()) {
            LOG.warn("Bundle: " + b.getSymbolicName());
        }
        throw new RuntimeException("Bundle " + symbolicName + " does not exist");
    }
        
    protected CamelContext createCamelContext() throws Exception {
        LOG.info("Get the bundleContext is " + bundleContext);
        LOG.info("Application installed as bundle id: " + bundleContext.getBundle().getBundleId());

        setThreadContextClassLoader();

        CamelContextFactory factory = new CamelContextFactory();
        factory.setBundleContext(bundleContext);
        factory.setRegistry(createRegistry());
        return factory.createContext();
    }
    
    protected void setThreadContextClassLoader() {
        // set the thread context classloader current bundle classloader
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    }
    
    public static UrlReference getCamelKarafFeatureUrl() {
        return getCamelKarafFeatureUrl(null);
    }

    public static UrlReference getCamelKarafFeatureUrl(String version) {

        String type = "xml/features";
        MavenArtifactProvisionOption mavenOption = mavenBundle().groupId("org.apache.camel.karaf").artifactId("apache-camel");
        if (version == null) {
            return mavenOption.versionAsInProject().type(type);
        } else {
            return mavenOption.version(version).type(type);
        }
    }
    
    public static UrlReference getKarafFeatureUrl() {
        String karafVersion = System.getProperty("karafVersion");
        LOG.info("*** The karaf version is " + karafVersion + " ***");

        String type = "xml/features";
        return mavenBundle().groupId("org.apache.karaf.assemblies.features").
            artifactId("standard").version(karafVersion).type(type);
    }

    public static UrlReference getKarafEnterpriseFeatureUrl() {
        String karafVersion = System.getProperty("karafVersion");
        LOG.info("*** The karaf version is " + karafVersion + " ***");

        String type = "xml/features";
        return mavenBundle().groupId("org.apache.karaf.assemblies.features").
            artifactId("enterprise").version(karafVersion).type(type);
    }

    public static Option loadCamelFeatures(String... features) {

        List<String> result = new ArrayList<String>();
        result.add("cxf-jaxb");
        result.add("camel-core");
        result.add("camel-spring");
        result.add("camel-test");
        for (String feature : features) {
            result.add(feature);
        }
        return scanFeatures(getCamelKarafFeatureUrl(), result.toArray(new String[4 + features.length]));
    }
              
    public static Option[] getDefaultCamelKarafOptions() {
        Option[] options =
        // Set the karaf environment with some customer configuration
            new Option[] {
                      karafDistributionConfiguration()
                          .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("tar.gz").versionAsInProject())
                          // This version is not actual karaf version that we use.
                          .karafVersion("2.3.0")
                          .name("Apache Karaf")
                          .useDeployFolder(false).unpackDirectory(new File("target/paxexam/unpack/")),

                      KarafDistributionOption.keepRuntimeFolder(),
                      // override the config.properties (to fix pax-exam bug)
                      replaceConfigurationFile("etc/config.properties", new File("src/test/resources/org/apache/camel/itest/karaf/config.properties")),
                      replaceConfigurationFile("etc/custom.properties", new File("src/test/resources/org/apache/camel/itest/karaf/custom.properties")),
                      // install the cxf jaxb spec as the karaf doesn't provide it by default
                      scanFeatures(getCamelKarafFeatureUrl(), "cxf-jaxb", "camel-core", "camel-spring", "camel-test")};

        return options;

    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions());

        // for remote debugging
        // vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"),

        return options;
    }

}
