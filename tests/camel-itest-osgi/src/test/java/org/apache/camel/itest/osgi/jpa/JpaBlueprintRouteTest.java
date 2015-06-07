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

@RunWith(PaxExam.class)
@Ignore("Does not work properly")
public class JpaBlueprintRouteTest extends OSGiBlueprintTestSupport {

    @Test
    public void testBlueprintRouteJpa() throws Exception {
        //getInstalledBundle("CamelBlueprintJpaTestBundle").start();
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintJpaTestBundle)", 20000);

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = ctx.createProducerTemplate();
        template.sendBodyAndHeader("direct:start", "someone@somewhere.org", "index", 1);
        template.sendBodyAndHeader("direct:start", "someoneelse@somewhere.org", "index", 2);

        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                provision(TinyBundles.bundle()
                    .add(SendEmail.class)
                    .add(MyProcessor.class)
                    .add("META-INF/persistence.xml", JpaBlueprintRouteTest.class.getResource("/META-INF/persistence.xml"))
                    .add("OSGI-INF/blueprint/test.xml", JpaBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                    .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintJpaTestBundle")
                    .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                    .set("Meta-Persistence", "META-INF/persistence.xml")
                    .build()),
               
                scanFeatures(getKarafEnterpriseFeatureUrl(), "jndi", "jpa", "transaction"),
                mavenBundle("org.apache.openjpa", "openjpa", "2.3.0"),
                mavenBundle("org.apache.derby", "derby", "10.4.2.0"),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-jpa"));
                //felix(), equinox());

        return options;
    }
}
