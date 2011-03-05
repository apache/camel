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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;
import org.osgi.service.blueprint.container.BlueprintContainer;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

/**
 * @version 
 */
@RunWith(JUnit4TestRunner.class)
public class CamelBlueprintTest extends OSGiBlueprintTestSupport {

    @Test
    public void testRouteWithAllComponents() throws Exception {
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle1)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle1").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle1)", 10000);
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle1)", 10000);
    }

    @Test
    public void testRouteWithMissingComponent() throws Exception {
        getInstalledBundle("org.apache.camel.camel-ftp").stop();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 500);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle2").start();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 500);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("org.apache.camel.camel-ftp").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 10000);
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle2)", 10000);
    }

    @Test
    public void testRouteWithMissingDataFormat() throws Exception {
        getInstalledBundle("org.apache.camel.camel-jackson").stop();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 500);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle3").start();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 500);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("org.apache.camel.camel-jackson").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 10000);
        getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle3)", 10000);
    }

    @Test
    public void testRouteWithPackage() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle4").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle4)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle4)", 10000);
        assertEquals(1, ctx.getRoutes().size());
    }

    @Test
    public void testRouteWithPackageScan() throws Exception {
        getInstalledBundle("CamelBlueprintTestBundle5").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle5)", 10000);
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=CamelBlueprintTestBundle5)", 10000);
        assertEquals(1, ctx.getRoutes().size());
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = options(

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-1.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle1")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-2.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle2")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-3.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle3")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-4.xml"))
                        .add(TestRouteBuilder.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle4")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-5.xml"))
                        .add(TestRouteBuilder.class)
                        .set(Constants.BUNDLE_SYMBOLICNAME, "CamelBlueprintTestBundle5")
                        .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                        .build()).noStart(),

                // install the spring dm profile
                profile("spring.dm").version("1.2.0"),
                // this is how you set the default log level when using pax logging (logProfile)
                // org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("TRACE"),

                // install blueprint requirements
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin"),
                // install tiny bundles
                mavenBundle("org.ops4j.base", "ops4j-base-store"),
                wrappedBundle(mavenBundle("org.ops4j.pax.swissbox", "pax-swissbox-bnd")),
                mavenBundle("org.ops4j.pax.swissbox", "pax-swissbox-tinybundles"),

                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-blueprint", "camel-test", "camel-ftp", "camel-jackson", "camel-jms"),

                workingDirectory("target/paxrunner/"),

//                vmOption("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"),

                felix(),
                equinox());

        return options;
    }

}
