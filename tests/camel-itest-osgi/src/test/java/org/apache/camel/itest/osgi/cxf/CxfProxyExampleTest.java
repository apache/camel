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

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class CxfProxyExampleTest extends OSGiIntegrationSpringTestSupport {

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
        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress("http://localhost:9080/camel-itest-osgi/webservices/incident");
        ReportIncidentEndpoint client = factory.create(ReportIncidentEndpoint.class);
        OutputReportIncident out = client.reportIncident(input);

        // assert we got a OK back
        assertEquals("OK;456", out.getCode());
        LOG.warn("Finish the testing");
    }

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/cxf/camel-config.xml"});
    }
   
    // TODO: CxfConsumer should use OSGi http service (no embedded Jetty)
    // TODO: Make this test work with OSGi
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
           
            // using the features to install the camel components
            loadCamelFeatures("camel-http", "cxf", "camel-cxf"),
                                        
            // need to install the generated src as the pax-exam doesn't wrap this bundles
            provision(TinyBundles.bundle()
                            .add(org.apache.camel.example.reportincident.InputReportIncident.class)
                            .add(org.apache.camel.example.reportincident.OutputReportIncident.class)
                            .add(org.apache.camel.example.reportincident.ReportIncidentEndpoint.class)
                            .add(org.apache.camel.example.reportincident.ReportIncidentEndpointService.class)
                            .add(org.apache.camel.example.reportincident.ObjectFactory.class)
                            .set("Export-Package", "org.apache.camel.example.reportincident")
                            .build(TinyBundles.withBnd())));
          
        return options;
    }

    
}
