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
package org.apache.camel.itest.osgi.blueprint;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.karaf.testing.Helper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

/**
 * @version 
 */
@RunWith(JUnit4TestRunner.class)
public class CamelBlueprint4Test extends OSGiBlueprintTestSupport {

    @Test
    public void testRouteWithXSLT() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle19").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle19)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle19)", 10000);

        ProducerTemplate template = ctx.createProducerTemplate();

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>");
        mock.message(0).body().isInstanceOf(String.class);

        template.sendBody("direct:start", "<hello>world!</hello>");

        mock.assertIsSatisfied();
        template.stop();
    }

    @Test
    public void testRouteWithVelocity() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle20").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle20)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle20)", 10000);

        ProducerTemplate template = ctx.createProducerTemplate();
        Object out = template.requestBody("direct:a", "world");
        assertEquals("<hello>world</hello>", out);
        template.stop();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                // Default karaf environment
                Helper.getDefaultOptions(
                        // this is how you set the default log level when using pax logging (logProfile)
                        Helper.setLogLevel("WARN")),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-19.xml"))
                        .add("org/apache/camel/itest/osgi/blueprint/example.xsl", OSGiBlueprintTestSupport.class.getResource("example.xsl"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle19")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-20.xml"))
                        .add("org/apache/camel/itest/osgi/blueprint/example.vm", OSGiBlueprintTestSupport.class.getResource("example.vm"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle20")
                        .build()).noStart(),
                        
                // install the spring, http features first
                scanFeatures(getKarafFeatureUrl(), "spring", "jetty"),

                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-test", "camel-blueprint", "camel-spring", "camel-velocity"),

                workingDirectory("target/paxrunner/"),

                felix(),
                equinox());

        return options;
    }

}
