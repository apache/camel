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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;

@RunWith(JUnit4TestRunner.class)
@Ignore("This test will be failed with CXF 2.4.1, we need to use CXF 2.4.2")
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
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CxfBlueprintRouterTest)", 10000);
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
                // using the features to install the karaf war feature
                scanFeatures(getKarafFeatureUrl(), "war"),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-cxf"),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", CxfBlueprintRouterTest.class.getResource("CxfBlueprintRouter.xml"))
                        .add(org.apache.camel.example.reportincident.InputReportIncident.class)
                        .add(org.apache.camel.example.reportincident.OutputReportIncident.class)
                        .add(org.apache.camel.example.reportincident.ReportIncidentEndpoint.class)
                        .add(org.apache.camel.example.reportincident.ReportIncidentEndpointService.class)
                        .add(org.apache.camel.example.reportincident.ObjectFactory.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CxfBlueprintRouterTest")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build(withBnd())).noStart(),
                vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5006")

        );

        return options;
    }
}
