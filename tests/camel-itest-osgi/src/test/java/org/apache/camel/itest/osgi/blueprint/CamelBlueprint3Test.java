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
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

/**
 * @version 
 */
@RunWith(JUnit4TestRunner.class)
public class CamelBlueprint3Test extends OSGiBlueprintTestSupport {

    @Test
    public void testRouteWithComponentFromBlueprint() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle6").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle6)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle6)", 10000);
        assertEquals(1, ctx.getRoutes().size());
        assertSame(ctn.getComponentInstance("seda"), ctx.getComponent("seda"));
    }

    @Test
    public void testRouteWithInterceptStrategy() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle7").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle7)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle7)", 10000);
        assertEquals(1, ctx.getRoutes().size());
        assertEquals(1, ctx.getInterceptStrategies().size());
        assertEquals(TestInterceptStrategy.class.getName(), ctx.getInterceptStrategies().get(0).getClass().getName());
    }

    @Test
    public void testComponentProperties() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle8").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle8)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle8)", 10000);
        assertEquals(1, ctx.getRoutes().size());
        assertEquals("direct://start", ctx.getRoutes().get(0).getEndpoint().getEndpointUri());
    }

    @Test
    public void testRouteBuilderRef() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle9").start();
        BlueprintContainer ctn = getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle9)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle9)", 10000);
        assertEquals(1, ctx.getRoutes().size());
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = combine(
                // Default karaf environment
                Helper.getDefaultOptions(
                // this is how you set the default log level when using pax logging (logProfile)
                     Helper.setLogLevel("WARN")),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-6.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle6")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-7.xml"))
                        .add(TestInterceptStrategy.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle7")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-8.xml"))
                        .add("org/apache/camel/component/properties/cheese.properties", OSGiBlueprintTestSupport.class.getResource("cheese.properties"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle8")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-9.xml"))
                        .add(TestRouteBuilder.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle9")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-blueprint", "camel-test", "camel-ftp", "camel-jackson", "camel-jms"),

                workingDirectory("target/paxrunner/"),

                felix(),
                equinox());

        return options;
    }

}
