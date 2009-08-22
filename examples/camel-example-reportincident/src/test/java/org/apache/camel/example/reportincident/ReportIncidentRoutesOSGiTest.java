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
package org.apache.camel.example.reportincident;


import org.apache.camel.CamelContext;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.osgi.CamelContextFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.mock_javamail.Mailbox;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundle;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;
import org.osgi.framework.BundleContext;

import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.asURL;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;
/**
 * Unit test of our routes
 */
@RunWith(JUnit4TestRunner.class)
public class ReportIncidentRoutesOSGiTest extends ReportIncidentRoutesTest {
    private static final transient Log LOG = LogFactory.getLog(ReportIncidentRoutesOSGiTest.class);
    
    @Inject
    protected BundleContext bundleContext;
    
    protected void startOSGiCamel() throws Exception {
        CamelContextFactory factory = new CamelContextFactory();
        factory.setBundleContext(bundleContext);
        LOG.info("Get the bundleContext is " + bundleContext);
        camel = factory.createContext();
        ReportIncidentRoutes routes = new ReportIncidentRoutes();
        routes.setUsingServletTransport(false);
        camel.addRoutes(routes);
        camel.start();
    }
    
    
    @Test
    public void testRendportIncident() throws Exception {
        startOSGiCamel();
        runTest();
        stopCamel();
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = options(
            // install the spring dm profile            
            profile("spring.dm").version("1.2.0"), 
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.apache.cxf.nofastinfoset").value("false"),
            org.ops4j.pax.exam.CoreOptions.systemProperty("xml.catalog.staticCatalog").value("false"),
            // using the features to install the camel components             
            scanFeatures(mavenBundle().groupId("org.apache.camel.karaf").
                         artifactId("features").versionAsInProject().type("xml/features"),                         
                          "camel-core", "camel-osgi", "camel-spring", "camel-test", "camel-velocity",  "camel-cxf"),
            
            // using the java mail API bundle
            mavenBundle().groupId("org.apache.servicemix.specs").artifactId("org.apache.servicemix.specs.javamail-api-1.4").version("1.3.0"),
                                        
            mavenBundle().groupId("org.apache.camel").artifactId("camel-mail").versionAsInProject(),
                          
            // Added the mock_java_mail bundle for testing
            mavenBundle().groupId("org.apache.camel.tests").artifactId("org.apache.camel.tests.mock-javamail_1.7").versionAsInProject(),
            
            // create a customer bundle start up the report incident bundle
            bundle(newBundle().addClass(InputReportIncident.class)
                .addClass(ObjectFactory.class)
                .addClass(OutputReportIncident.class)
                .addClass(ReportIncidentRoutesOSGiTest.class)
                .addClass(ReportIncidentRoutesTest.class)
                .addClass(ReportIncidentRoutes.class)
                .addClass(MyBean.class)
                .addClass(FilenameGenerator.class)
                .addClass(ReportIncidentEndpoint.class)
                .addClass(ReportIncidentEndpointService.class)
                .addResource("etc/report_incident.wsdl", ReportIncidentRoutesTest.class.getResource("/etc/report_incident.wsdl"))
                .addResource("etc/MailBody.vm", ReportIncidentRoutesTest.class.getResource("/etc/MailBody.vm"))
                .prepare(withBnd().set(Constants.BUNDLE_SYMBOLICNAME, "CamelExampleReportIncidentBundle")
                         .set(Constants.EXPORT_PACKAGE, "org.apache.camel.example.reportincident,etc")).build(asURL()).toString()),
            
            
            felix());
        
        return options;
    }
   
}
