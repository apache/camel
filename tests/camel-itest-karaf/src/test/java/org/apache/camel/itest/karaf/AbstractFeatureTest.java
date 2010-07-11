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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.osgi.CamelContextFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.FrameworkOption;
import org.ops4j.pax.exam.options.UrlReference;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

public abstract class AbstractFeatureTest {

    protected final transient Log log = LogFactory.getLog(getClass());

    @Inject
    protected BundleContext bundleContext;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    protected void testComponent(String component) throws Exception {
        long max = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                assertNotNull(createCamelContext().getComponent(component));
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
                    continue;
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
                    continue;
                } else {
                    throw t;
                }
            }
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContextFactory factory = new CamelContextFactory();
        factory.setBundleContext(bundleContext);
        log.info("Get the bundleContext is " + bundleContext);
        return factory.createContext();
    }

    public static String extractName(Class clazz) {
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
        String springVersion = System.getProperty("springVersion");
        System.out.println("*** The spring version is " + springVersion + " ***");
        String type = "xml/features"; 
        if (springVersion != null && springVersion.startsWith("2")) {
            type = "xml/features-spring2";
        }
        return mavenBundle().groupId("org.apache.camel.karaf").
            artifactId("apache-camel").versionAsInProject().type(type);
    }
    
    public static FrameworkOption getFramework() {
        return felix();
    }
    
    public static Option[] configure(String feature) {
        return configure(feature, getFramework());
    }

    public static Option[] configure(String feature, FrameworkOption framework) {
        Option[] options = options(
            profile("log").version("1.4"),
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"),

            //need to install some karaf features
            mavenBundle("org.apache.felix", "org.apache.felix.configadmin").versionAsInProject(),
            
            mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.jaxp-ri").version("1.4.2_4-SNAPSHOT"),
            
            scanFeatures(getCamelKarafFeatureUrl(),
                          "camel-spring", "camel-" + feature),
            workingDirectory("target/paxrunner/"),            

            framework);
            

        return options;
    }

    

}