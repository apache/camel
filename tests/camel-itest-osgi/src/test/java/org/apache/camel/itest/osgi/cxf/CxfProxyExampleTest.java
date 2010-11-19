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
package org.apache.camel.itest.osgi.cxf;

import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;
import org.apache.camel.example.reportincident.ReportIncidentEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.bootClasspathLibrary;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;

@RunWith(JUnit4TestRunner.class)
public class CxfProxyExampleTest extends OSGiIntegrationSpringTestSupport {

    protected static ReportIncidentEndpoint createCXFClient() {
        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress("http://localhost:9080/camel-itest-osgi/webservices/incident");
        return (ReportIncidentEndpoint) factory.create();
    }

    @Test
    public void testCxfProxy() throws Exception {
        // create input parameter
        InputReportIncident input = new InputReportIncident();
        input.setIncidentId("123");
        input.setIncidentDate("2010-09-28");
        input.setGivenName("Claus");
        input.setFamilyName("Ibsen");
        input.setSummary("Bla");
        input.setDetails("Bla bla");
        input.setEmail("davsclaus@apache.org");
        input.setPhone("12345678");

        // create the webservice client and send the request
        ReportIncidentEndpoint client = createCXFClient();
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        assertEquals("OK;456", out.getCode());
    }

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/cxf/camel-config.xml"});
    }
   
    // TODO: CxfConsumer should use OSGi http service (no embedded Jetty)
    // TODO: Make this test work with OSGi

    @Configuration
    public static Option[] configure() {
        Option[] options = options(
            
            profile("log"),
            profile("compendium"),
            
            systemPackage("com.sun.xml.bind.marshaller"),
            systemPackage("com.sun.org.apache.xerces.internal.dom"),
            systemPackage("com.sun.org.apache.xerces.internal.jaxp"),
         
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),

            // need to install some karaf features
            scanFeatures(getKarafFeatureUrl(), "http"),
            
            // using the features to install the camel components
            scanFeatures(getCamelKarafFeatureUrl(),
                          "spring", "spring-dm", "camel-core", "camel-spring", "camel-http", "camel-test", "camel-cxf"),
                                  
            // need to install the generated src as the pax-exam doesn't wrap this bundles
            provision(newBundle()
                      .add(org.apache.camel.example.reportincident.InputReportIncident.class)
                      .add(org.apache.camel.example.reportincident.OutputReportIncident.class)
                      .add(org.apache.camel.example.reportincident.ReportIncidentEndpoint.class)
                      .add(org.apache.camel.example.reportincident.ReportIncidentEndpointService.class)
                      .add(org.apache.camel.example.reportincident.ObjectFactory.class)
                      .build(withBnd())),
                      
            workingDirectory("target/paxrunner/"),
            
            felix());

        return options;
    }
}
