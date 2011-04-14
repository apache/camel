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
package org.apache.camel.itest.osgi.script;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.karaf.testing.Helper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

/**
 * Test camel-script for groovy expressions in OSGi
 */
@RunWith(JUnit4TestRunner.class)
public class GroovyScriptOsgiTest extends OSGiIntegrationTestSupport {
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setBody().groovy("request.body * 2").to("mock:result");
            }
        };
    }

    @Test
    public void testLanguage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(6);

        template.sendBody("direct:start", 3);

        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            // Default karaf environment
            Helper.getDefaultOptions(
            // this is how you set the default log level when using pax logging (logProfile)
                Helper.setLogLevel("WARN")),
                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-spring", "camel-test", "camel-script"),
                        
                mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.ant").version("1.7.0_3"),
                mavenBundle().groupId("org.codehaus.groovy").artifactId("groovy-all").version("1.7.9"),

                workingDirectory("target/paxrunner/"),

                felix(), equinox());

        return options;
    }
}
