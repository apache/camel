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

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.krati.KratiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.blueprint.OSGiBlueprintTestSupport;
import org.apache.karaf.testing.Helper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Customizer;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.modifyBundle;

@RunWith(JUnit4TestRunner.class)
public class KratiBlueprintRouteTest extends OSGiBlueprintTestSupport {

    protected OsgiBundleXmlApplicationContext applicationContext;

    @Inject
    protected BundleContext bundleContext;

    @Test
    public void testProducerConsumerAndIdempotent() throws Exception {
        getInstalledBundle("CamelBlueprintKratiTestBundle").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintKratiTestBundle)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintKratiTestBundle)", 10000);
        MockEndpoint mock = (MockEndpoint) ctx.getEndpoint("mock:results");
        ProducerTemplate template = ctx.createProducerTemplate();
        mock.expectedMessageCount(2);
        template.sendBodyAndHeader("direct:put", new SomeObject("1", "Test 1"), KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", new SomeObject("2", "Test 2"), KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", new SomeObject("3", "Test 3"), KratiConstants.KEY, "1");
        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
                // Default karaf environment
                Helper.getDefaultOptions(
                        // this is how you set the default log level when using pax logging (logProfile)
                        Helper.setLogLevel("INFO")),
                new Customizer() {
                    @Override
                    public InputStream customizeTestProbe(InputStream testProbe) {
                        return modifyBundle(testProbe)
                                .add(SomeObject.class)
                                .add("META-INF/persistence.xml", KratiBlueprintRouteTest.class.getResource("/META-INF/persistence.xml"))
                                .add("OSGI-INF/blueprint/test.xml", KratiBlueprintRouteTest.class.getResource("blueprintCamelContext.xml"))
                                .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintKratiTestBundle")
                                .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                                .build();
                    }
                },
                // install the spring.
                scanFeatures(getKarafFeatureUrl(), "spring"),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-blueprint", "camel-test", "camel-krati"),

                workingDirectory("target/paxrunner/"),
                //vmOption("-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                felix(), equinox());

        return options;
    }
}