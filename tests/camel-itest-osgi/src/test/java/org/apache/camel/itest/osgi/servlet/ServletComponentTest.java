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
package org.apache.camel.itest.osgi.servlet;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.karaf.testing.Helper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.OptionUtils.combine;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;

@RunWith(JUnit4TestRunner.class)
public class ServletComponentTest extends OSGiIntegrationSpringTestSupport {
    
    private static final String CONTEXT_PATH = "/org/apache/camel/itest/osgi/servlet/ServletComponentTest-context.xml";

    @Test
    public void testSendMessage() {
        String endpointURI = "http://localhost:9080/camel/services/hello";
        String response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("Echo Hello World", response);
    }
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(
            // Default karaf environment
            Helper.getDefaultOptions(
            // this is how you set the default log level when using pax logging (logProfile)
                Helper.setLogLevel("WARN")),
            Helper.loadKarafStandardFeatures("spring", "jetty", "http", "war"),
            // set the system property for pax web
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.osgi.service.http.port").value("9080"),
            
            // using the features to install the camel components             
            scanFeatures(getCamelKarafFeatureUrl(),                         
                          "camel-core", "camel-spring", "camel-test", "camel-http", "camel-servlet"),

            workingDirectory("target/paxrunner/"),

            felix(),
            equinox());
        
        return options;
    }
    
    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[] {CONTEXT_PATH});
    }

}
