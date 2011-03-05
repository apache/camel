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
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Constants;

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
public class OSGiBlueprintHelloWorldTest extends OSGiBlueprintTestSupport {

    private String name = OSGiBlueprintHelloWorldTest.class.getName();

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/paxrunner/target/foo");
        super.setUp();
    }

    @Test
    public void testHelloWorld() throws Exception {
        // start bundle
        getInstalledBundle(name).start();

        // must use the camel context from osgi
        CamelContext ctx = getOsgiService(CamelContext.class, "(camel.context.symbolicname=" + name + ")", 10000);

        ProducerTemplate myTemplate = ctx.createProducerTemplate();
        myTemplate.start();

        // do our testing
        MockEndpoint mock = ctx.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        myTemplate.sendBodyAndHeader("file:target/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();

        myTemplate.stop();
    }

    @Configuration
    public static Option[] configure() throws Exception {

        Option[] options = options(

                bundle(newBundle()
                        .add("OSGI-INF/blueprint/test.xml", OSGiBlueprintTestSupport.class.getResource("blueprint-13.xml"))
                        .set(Constants.BUNDLE_SYMBOLICNAME, OSGiBlueprintHelloWorldTest.class.getName())
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
                        "camel-core", "camel-blueprint", "camel-test"),

                workingDirectory("target/paxrunner/"),

                felix(), equinox());

        return options;
    }

}
