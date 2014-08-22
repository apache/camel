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

import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Constants;

import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.OptionUtils.combine;

/**
 * CAMEL-5722: Test to verify that routes sitting in different bundles but listening on the same Jetty port,
 * and thus, sharing the same container-wide Jetty Connector, do not share the classloader. The TCCL should
 * be different in each case, as for each route it should be the Classloader of their containing bundle.
 */
@RunWith(PaxExam.class)
public class OSGiJettyCamelContextsClassloaderTest extends OSGiIntegrationTestSupport {
    
    @Test
    public void testClassloadersAreCongruent() throws Exception {
        // Wait a while to let all the service started
        Thread.sleep(3000);
        // test context 1
        String endpointURI1 = "http://localhost:9010/camel-context-1/continuation/";
        String response1 = template.requestBody(endpointURI1, "Hello World", String.class);
        System.out.println("Response from Context 1: " + response1);
        assertEquals("Camel Context 1 classloaders unequal", "true", response1.split(" --- ")[0]);
        
        // test context 2
        String endpointURI2 = "http://localhost:9010/camel-context-2/continuation/";
        String response2 = template.requestBody(endpointURI2, "Hello World", String.class);
        System.out.println("Response from Context 2: " + response2);
        assertEquals("Camel Context 2 classloaders unequal", "true", response2.split(" --- ")[0]);
        
        // contexts's both classloaders toString() representation must contain the bundle symbolic ID
        // definition of "both classloaders": the Camel Context classloader and the Thread classloader during processing
        assertTrue(response1.matches(".*CamelContextBundle1.*CamelContextBundle1.*"));
        assertTrue(response2.matches(".*CamelContextBundle2.*CamelContextBundle2.*"));
        
        // Wait a while to let all the service started
        Thread.sleep(3000);
        // test context 1
        endpointURI1 = "http://localhost:9010/camel-context-1/noContinuation/";
        response1 = template.requestBody(endpointURI1, "Hello World", String.class);
        System.out.println("Response from Context 1: " + response1);
        assertEquals("Camel Context 1 classloaders unequal", "true", response1.split(" --- ")[0]);
        
        // test context 2
        endpointURI2 = "http://localhost:9010/camel-context-2/noContinuation/";
        response2 = template.requestBody(endpointURI2, "Hello World", String.class);
        System.out.println("Response from Context 2: " + response2);
        assertEquals("Camel Context 2 classloaders unequal", "true", response2.split(" --- ")[0]);
        
        // contexts's both classloaders toString() representation must contain the bundle symbolic ID
        // definition of "both classloaders": the Camel Context classloader and the Thread classloader during processing
        assertTrue(response1.matches(".*CamelContextBundle1.*CamelContextBundle1.*"));
        assertTrue(response2.matches(".*CamelContextBundle2.*CamelContextBundle2.*"));
        
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-jetty"),
            //set up the camel context bundle1          
            provision(TinyBundles.bundle().add("META-INF/spring/Classloader-CamelContext1.xml", OSGiJettyCamelContextsClassloaderTest.class.getResource("Classloader-CamelContext1.xml"))
                      .add(JettyClassloaderCheckProcessor.class)
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle1")
                      .set(Constants.BUNDLE_NAME, "CamelContext1")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .build()),
                  
            //set up the camel context bundle1          
            provision(TinyBundles.bundle().add("META-INF/spring/Classloader-CamelContext2.xml", OSGiJettyCamelContextsClassloaderTest.class.getResource("Classloader-CamelContext2.xml"))
                      .add(JettyClassloaderCheckProcessor.class)
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle2")
                      .set(Constants.BUNDLE_NAME, "CamelContext2")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .build()));
        
        return options;
    }
    

}
