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
package org.apache.camel.itest.osgi.core.xslt;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class XsltBlueprintRouteTest extends OSGiBlueprintTestSupport {
    private CamelContext camelContext;
    private ProducerTemplate mytemplate;
    
    @Test
    public void testSendMessageAndHaveItTransformed() throws Exception {
        MockEndpoint endpoint = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        endpoint.expectedMessageCount(1);

        mytemplate.sendBody("direct:start",
                "<mail><subject>Hey</subject><body>Hello world!</body></mail>");

        assertMockEndpointsSatisfied();

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
        mytemplate.stop();
    }
    
    protected void doPostSetup() throws Exception {
        getInstalledBundle("XsltBlueprintRouteTest").start();
        camelContext = getOsgiService(CamelContext.class, "(camel.context.symbolicname=XsltBlueprintRouteTest)", 10000);
        mytemplate = camelContext.createProducerTemplate();
        mytemplate.start();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint"),

                bundle(TinyBundles.bundle()
                        .add("OSGI-INF/blueprint/test.xml", XsltBlueprintRouteTest.class.getResource("XsltBlueprintRouter.xml"))
                        .add("transform.xsl", XsltBlueprintRouteTest.class.getResource("transform.xsl"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "XsltBlueprintRouteTest")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build(TinyBundles.withBnd())).noStart()

        );

        return options;
    }


}
