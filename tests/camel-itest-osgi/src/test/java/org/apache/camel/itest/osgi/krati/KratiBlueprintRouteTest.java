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
package org.apache.camel.itest.osgi.krati;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.krati.KratiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class KratiBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testProducerConsumerAndIdempotent() throws Exception {
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintKratiTestBundle)", 20000);

        MockEndpoint mock = ctx.getEndpoint("mock:results", MockEndpoint.class);
        ProducerTemplate template = ctx.createProducerTemplate();
        mock.expectedMessageCount(2);

        template.sendBodyAndHeader("direct:put", new SomeObject("1", "Test 1"), KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", new SomeObject("2", "Test 2"), KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", new SomeObject("3", "Test 3"), KratiConstants.KEY, "1");

        assertMockEndpointsSatisfied();

        template.stop();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(

                getDefaultCamelKarafOptions(),
                // using the features to install the camel components
                loadCamelFeatures("camel-blueprint", "camel-krati"),

                provision(TinyBundles.bundle()
                                .add(SomeObject.class)
                                .add("META-INF/persistence.xml", KratiBlueprintRouteTest.class.getResource("/META-INF/persistence.xml"))
                                .add("OSGI-INF/blueprint/test.xml", KratiBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                                .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintKratiTestBundle")
                                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                                .build()));

        return options;
    }
}