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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

/**
 * Test camel-script for Ruby expressions in OSGi
 */
@RunWith(JUnit4TestRunner.class)
@Ignore("You need an OSGi-ified version of JRuby for this to pass")
public class RubyOsgiTest extends OSGiIntegrationTestSupport {
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").setBody().ruby("$request.body + $request.body").to("mock:finish");
            }
        };
    }

    @Test
    public void testSendMessage() throws Exception {
        MockEndpoint mock = getMandatoryEndpoint("mock:finish", MockEndpoint.class);
        assertNotNull("The mock endpoint should not be null", mock);

        mock.expectedBodiesReceived("HelloHello");
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = options(

                // install the spring dm profile
                profile("spring.dm").version("1.2.0"),
                // this is how you set the default log level when using pax logging (logProfile)
                org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),

                // using the features to install the camel components
                scanFeatures(getCamelKarafFeatureUrl(),
                        "camel-core", "camel-spring", "camel-test", "camel-script", "camel-ruby"),

                workingDirectory("target/paxrunner/"),

                felix(), equinox());

        return options;
    }
}
