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

package org.apache.camel.itest.osgi.jpa;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Customizer;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

//import static org.ops4j.pax.exam.CoreOptions.equinox;
//import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.modifyBundle;


@RunWith(JUnit4TestRunner.class)
public class JpaBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testBlueprintRouteJpa() throws Exception {
        getInstalledBundle("CamelBlueprintJpaTestBundle").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintJpaTestBundle)", 30000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintJpaTestBundle)", 20000);

        MockEndpoint mock = (MockEndpoint) ctx.getEndpoint("mock:result");
        mock.expectedMessageCount(1);

        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBody("direct:start", new SendEmail(1L, "someone@somewhere.org"));
        template.sendBody("direct:start", new SendEmail(2L, "someoneelse@somewhere.org"));

        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                new Customizer() {
                    @Override
                    public InputStream customizeTestProbe(InputStream testProbe) {
                        return modifyBundle(testProbe)
                                .add(SendEmail.class)
                                .add("META-INF/persistence.xml", JpaBlueprintRouteTest.class.getResource("/META-INF/persistence.xml"))
                                .add("OSGI-INF/blueprint/test.xml", JpaBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                                .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintJpaTestBundle")
                                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                                .set("Meta-Persistence", "META-INF/persistence.xml")
                                .build();
                    }
                },
                scanFeatures(getKarafEnterpriseFeatureUrl(), "jndi", "jpa", "transaction"),
                mavenBundle("org.apache.derby", "derby", "10.4.2.0"),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-jpa"));
                //felix(), equinox());

        return options;
    }
}
