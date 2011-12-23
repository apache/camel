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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;

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

    @Test
    public void testSetHeaderXPathResultType() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle21").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle21)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle21)", 10000);

        ProducerTemplate template = ctx.createProducerTemplate();

        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.message(0).header("foo").isInstanceOf(Boolean.class);
        mock.message(0).header("foo").isEqualTo(true);
        mock.message(0).header("bar").isInstanceOf(Boolean.class);
        mock.message(0).header("bar").isEqualTo(false);

        template.sendBody("direct:start", "<hello>World</hello>");

        mock.assertIsSatisfied();
        template.stop();
    }

    @Test
    public void testGetApplicationContextClassloader() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle22").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle22)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle22)", 10000);

        // test the application context classloader
        assertNotNull("The application context classloader should not be null", ctx.getApplicationContextClassLoader());
        ClassLoader cl = ctx.getApplicationContextClassLoader();
        assertNotNull("It should load the TestRouteBuilder class", cl.getResource("OSGI-INF/blueprint/test.xml"));
        assertNotNull("It should load the TestRouteBuilder class", cl.loadClass("org.apache.camel.itest.osgi.blueprint.TestRouteBuilder"));

    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                getDefaultCamelKarafOptions(),

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
                
                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-21.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle21")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-13.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle22")
                        .add(TestRouteBuilder.class)
                        .build(withBnd())).noStart(),

                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-blueprint", "camel-velocity"));

        return options;
    }

}
