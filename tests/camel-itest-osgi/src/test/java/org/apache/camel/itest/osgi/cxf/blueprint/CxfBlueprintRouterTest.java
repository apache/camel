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
package org.apache.camel.itest.osgi.cxf.blueprint;

import org.apache.camel.CamelContext;
import org.apache.camel.example.reportincident.InputReportIncident;
import org.apache.camel.example.reportincident.OutputReportIncident;
import org.apache.camel.example.reportincident.ReportIncidentEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.apache.camel.itest.osgi.cxf.ReportIncidentEndpointService;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class CxfBlueprintRouterTest extends OSGiBlueprintTestSupport {
    private static Server server;

    @BeforeClass
    public static void startServer() {
        JaxWsServerFactoryBean sf = new JaxWsServerFactoryBean();
        sf.setAddress("http://localhost:9002/cxf");
        sf.setServiceBean(new ReportIncidentEndpointService());
        server = sf.create();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    protected void doPostSetup() throws Exception {
        getInstalledBundle("CxfBlueprintRouterTest").start();
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CxfBlueprintRouterTest)", 20000);
    }

    protected static ReportIncidentEndpoint createCXFClient() {
        // we use CXF to create a client for us as its easier than JAXWS and works
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ReportIncidentEndpoint.class);
        factory.setAddress("http://localhost:9000/incident");
        return (ReportIncidentEndpoint) factory.create();
    }


    @Test
    public void testRouter() throws Exception {
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
        assertEquals("OK;123", out.getCode());
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures(
                        "camel-blueprint", "camel-cxf"),

                bundle(TinyBundles.bundle()
                       .add("OSGI-INF/blueprint/test.xml", CxfBlueprintRouterTest.class.getResource("CxfBlueprintRouter.xml"))
                       .add("WSDL/report_incident.wsdl", CxfBlueprintRouterTest.class.getResource("/report_incident.wsdl"))
                       .add(org.apache.camel.example.reportincident.InputReportIncident.class)
                       .add(org.apache.camel.example.reportincident.OutputReportIncident.class)
                       .add(org.apache.camel.example.reportincident.ReportIncidentEndpoint.class)
                       .add(org.apache.camel.example.reportincident.ReportIncidentEndpointService.class)
                       .add(org.apache.camel.example.reportincident.ObjectFactory.class)
                       .set("Export-Package", "org.apache.camel.example.reportincident")
                       .set(Constants.BUNDLE_SYMBOLICNAME, "CxfBlueprintRouterTest")
                       .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                       .build(TinyBundles.withBnd())).noStart()

        );

        return options;
    }
}
