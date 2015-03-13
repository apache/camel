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

package org.apache.camel.itest.osgi.xstream;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class XstreamBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        getInstalledBundle("CamelBlueprintXstreamTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintXstreamTestBundle)", 10000);

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String body = "<org.apache.camel.itest.osgi.xstream.SampleObject>\n"
                + "    <id>1</id>\n"
                + "    <value>test</value>\n"
                + "</org.apache.camel.itest.osgi.xstream.SampleObject>";

        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:start", body);
        mock.assertIsSatisfied();
        Object result = mock.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(new SampleObject("1"), result);
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(TestProbeBuilder builder) {
        builder.setHeader(Constants.EXPORT_PACKAGE, SampleObject.class.getPackage().getName());
        return builder;
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                provision(TinyBundles.bundle()
                    .add(XstreamRouteBuilder.class)
                    .add("OSGI-INF/blueprint/test.xml", XstreamBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                    .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintXstreamTestBundle")
                    .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                    .build()),

                // using the features to install the camel components
                loadCamelFeatures("xml-specs-api", "camel-blueprint", "camel-xstream"));
                //vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                

        return options;
    }

}
