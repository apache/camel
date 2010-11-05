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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.store.Store;
import org.ops4j.store.StoreFactory;
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
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;

@RunWith(JUnit4TestRunner.class)
public class OSGiBlueprintTestSupport extends AbstractIntegrationTest {

    @Test
    public void testRouteWithAllComponents() throws Exception {
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle1)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle1").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle1)", 5000);
    }

    @Test
    public void testRouteWithMissingComponent() throws Exception {
        getInstalledBundle("org.apache.camel.camel-mail").stop();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle2").start();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("org.apache.camel.camel-mail").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle2)", 5000);
    }

    @Test
    public void testRouteWithMissingDataFormat() throws Exception {
        getInstalledBundle("org.apache.camel.camel-jaxb").stop();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("CamelBlueprintTestBundle3").start();
        try {
            getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 1000);
            fail("The blueprint container should not be available");
        } catch (Exception e) {
        }
        getInstalledBundle("org.apache.camel.camel-jaxb").start();
        getOsgiService(BlueprintContainer.class, "(osgi.blueprint.container.symbolicname=CamelBlueprintTestBundle3)", 5000);
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
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

            // install the spring dm profile
            profile("spring.dm").version("1.2.0"),
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),

            // install blueprint requirements
            mavenBundle("org.apache.felix", "org.apache.felix.configadmin"),
            // install tiny bundles
            mavenBundle("org.ops4j.base", "ops4j-base-store"),
            wrappedBundle(mavenBundle("org.ops4j.pax.swissbox", "pax-swissbox-bnd")),
            mavenBundle("org.ops4j.pax.swissbox", "pax-swissbox-tinybundles"),

            // using the features to install the camel components
            scanFeatures(getCamelKarafFeatureUrl(),
                          "camel-core", "camel-blueprint", "camel-test", "camel-mail", "camel-jaxb"),

            workingDirectory("target/paxrunner/"),

            //felix(),
            equinox());

        return options;
    }

    private static void copy(InputStream input, OutputStream output, boolean close) throws IOException {
        try {
            byte[] buf = new byte[8192];
            int bytesRead = input.read(buf);
            while (bytesRead != -1) {
                output.write(buf, 0, bytesRead);
                bytesRead = input.read(buf);
            }
            output.flush();
        } finally {
            if (close) {
                close(input, output);
            }
        }
    }

    private static void close(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable c : closeables) {
                try {
                    c.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private static UrlProvisionOption bundle(final InputStream stream) throws IOException {
        Store<InputStream> store = StoreFactory.defaultStore();
        return new UrlProvisionOption(store.getLocation(store.store(stream)).toURL().toExternalForm());
    }

}
