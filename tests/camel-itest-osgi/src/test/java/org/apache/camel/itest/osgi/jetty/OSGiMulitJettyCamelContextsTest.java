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

package org.apache.camel.itest.osgi.jetty;

import java.net.URL;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.apache.camel.management.DefaultManagementNamingStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.swissbox.tinybundles.dp.Constants;

import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.profile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.scanFeatures;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.workingDirectory;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;

@RunWith(JUnit4TestRunner.class)
public class OSGiMulitJettyCamelContextsTest extends OSGiIntegrationTestSupport {
   
    @Test
    public void testStoppingJettyContext() throws Exception {
        // Wait a while to let all the service started
        Thread.sleep(3000);
        String endpointURI = "http://localhost:9010/context1/";
        String response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("response is " , "camelContext1", response);
        
        endpointURI = "http://localhost:9010/context2/";
        response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("response is " , "camelContext2", response);
        
        getInstalledBundle("org.apache.camel.itest.osgi.CamelContextBundle1").uninstall();
        
        endpointURI = "http://localhost:9010/context1/";
        try {
            response = template.requestBody(endpointURI, "Hello World", String.class);
            fail("We are expect the exception here");
        } catch (Exception ex) {
            assertTrue("Get the wrong exception.", ex instanceof CamelExecutionException);
        }
        
        endpointURI = "http://localhost:9010/context2/";
        response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("response is " , "camelContext2", response);
    }
    
    
    @Configuration
    public static Option[] configure() throws Exception {
        
        Option[] options = options(
            // install the spring dm profile            
            profile("spring.dm").version("1.2.0"),    
            // this is how you set the default log level when using pax logging (logProfile)
            org.ops4j.pax.exam.CoreOptions.systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
            
            // using the features to install the camel components             
            scanFeatures(getCamelKarafFeatureUrl(),                         
                          "camel-core", "camel-spring", "camel-test", "camel-jetty"),
            //set up the camel context bundle1          
            provision(newBundle().add("META-INF/spring/CamelContext1.xml", OSGiMulitJettyCamelContextsTest.class.getResource("CamelContext1.xml"))
                      .add(JettyProcessor.class)
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle1")
                      .set(Constants.BUNDLE_NAME, "CamelContext1")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .build()),
                  
            //set up the camel context bundle1          
            provision(newBundle().add("META-INF/spring/CamelContext2.xml", OSGiMulitJettyCamelContextsTest.class.getResource("CamelContext2.xml"))
                      .add(JettyProcessor.class)           
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle2")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .set(Constants.BUNDLE_NAME, "CamelContext2").build()),
                      
            
            workingDirectory("target/paxrunner/"),
             
            equinox(),
            felix());
        
        return options;
    }

}
