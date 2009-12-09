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
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.itest.osgi.servlet.support.ServletActivator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.SystemPropertyOption;
import org.ops4j.pax.swissbox.tinybundles.core.metadata.BndBuilder;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;
import org.osgi.service.http.HttpService;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;


import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;

@RunWith(JUnit4TestRunner.class)
public class ServletComponentTest extends OSGiIntegrationSpringTestSupport {
    
    @Test
    public void testSendMessage() {
        String endpointURI = "http://localhost:9080/camel/services/hello";
        String response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("response is " , "Echo Hello World", response);
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = options(
            
            // install the spring dm profile            
            profile("spring.dm").version("1.2.0"),
            profile("compendium"),
            profile("web"),
            // set the system property for pax web
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.osgi.service.http.port").value("9080"),
            
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
            
            // using the features to install the camel components             
            scanFeatures(mavenBundle().groupId("org.apache.camel.karaf").
                         artifactId("features").versionAsInProject().type("xml/features"),                         
                          "camel-core", "camel-spring-osgi", "camel-test", "camel-http", "camel-servlet"),
          
                
            felix());
        
        return options;
    }
    
    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/servlet/ServletService.xml",
                                                                "org/apache/camel/itest/osgi/servlet/CamelServletContext.xml"});
    }
   


}
