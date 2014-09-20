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

import org.apache.camel.CamelExecutionException;
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

@RunWith(PaxExam.class)
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
            template.requestBody(endpointURI, "Hello World", String.class);
            fail("We are expect the exception here");
        } catch (Exception ex) {
            assertTrue("Get the wrong exception.", ex instanceof CamelExecutionException);
        }
        
        endpointURI = "http://localhost:9010/context2/";
        response = template.requestBody(endpointURI, "Hello World", String.class);
        assertEquals("response is " , "camelContext2", response);
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-jetty"),
            //set up the camel context bundle1          
            provision(TinyBundles.bundle().add("META-INF/spring/CamelContext1.xml", OSGiMulitJettyCamelContextsTest.class.getResource("CamelContext1.xml"))
                      .add(JettyProcessor.class)
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle1")
                      .set(Constants.BUNDLE_NAME, "CamelContext1")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .build()),
                  
            //set up the camel context bundle1          
            provision(TinyBundles.bundle().add("META-INF/spring/CamelContext2.xml", OSGiMulitJettyCamelContextsTest.class.getResource("CamelContext2.xml"))
                      .add(JettyProcessor.class)           
                      .set(Constants.BUNDLE_SYMBOLICNAME, "org.apache.camel.itest.osgi.CamelContextBundle2")
                      .set(Constants.DYNAMICIMPORT_PACKAGE, "*")
                      .set(Constants.BUNDLE_NAME, "CamelContext2").build())   
        );
        
        return options;
    }
    

}
