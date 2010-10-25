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
package org.apache.camel.itest.osgi.exec;

import org.apache.camel.builder.RouteBuilder;
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

@RunWith(JUnit4TestRunner.class)
@Ignore("We need a test which runs on all platforms")
public class ExecTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testExec() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("test");
        template.sendBody("direct:exec", "test");
        assertMockEndpointsSatisfied();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:exec").to("exec:echo?args=-n test").to("mock:result");
            }
        };
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
                              "camel-core", "camel-spring", "camel-test", "camel-exec"),
                
                workingDirectory("target/paxrunner/"),

                felix(), equinox());
        
        return options;
    }
    
}
