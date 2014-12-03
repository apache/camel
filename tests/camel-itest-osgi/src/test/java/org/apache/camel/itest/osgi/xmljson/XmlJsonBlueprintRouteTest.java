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

package org.apache.camel.itest.osgi.xmljson;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * OSGi integration test for camel-xmljson
 */
@Ignore("see CAMEL-8029 and the <details> XML tag of camel-xmljson Karaf feature about running this test")
@RunWith(PaxExam.class)
public class XmlJsonBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        getInstalledBundle("CamelBlueprintXmlJsonTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintXmlJsonTestBundle)", 10000);

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String body = "<hello>\n"
                + "    <name>Raul</name>\n"
                + "    <surname>Kripalani</surname>\n"
                + "</hello>";

        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:start", body);
        mock.assertIsSatisfied();
        String result = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String spacesRemoved = result.replaceAll("\\s+", result);
        assertEquals("{\"name\":\"Raul\",\"surname\":\"Kripalani\"}", spacesRemoved);
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                provision(TinyBundles.bundle()
                    .add(XmlJsonRouteBuilder.class)
                    .add("OSGI-INF/blueprint/test.xml", XmlJsonBlueprintRouteTest.class.getResource("xmlJsonBlueprintCamelContext.xml"))
                    .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintXmlJsonTestBundle")
                    .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                    .build()),
                   
                // using the features to install the camel components
                loadCamelFeatures("xml-specs-api", "camel-blueprint", "camel-xmljson"));
                //vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                

        return options;
    }

}
