/*
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
package org.apache.camel.itest.greeter;

import java.io.File;

import javax.xml.ws.Endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringTest
@ContextConfiguration
public class CamelGreeterFileDomTest {
    private static final Logger LOG = LoggerFactory.getLogger(CamelGreeterFileDomTest.class);
    
    private static final String REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><greetMe xmlns=\"http://apache.org/hello_world_soap_http/types\">"
        + "<requestType>Willem</requestType></greetMe></soap:Body></soap:Envelope>";
    private static Endpoint endpoint;
    private static int port = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("CamelGreeterFileDomTest.port", Integer.toString(port));
    }
    @Autowired
    protected CamelContext camelContext;
    
    @BeforeAll
    public static void startServer() {
        // Start the Greeter Server
        Object implementor = new GreeterImpl();
        String address = "http://localhost:" + port + "/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, implementor);
        LOG.info("The WS endpoint is published! ");
    }

    @AfterAll
    public static void stopServer() {
        // Shutdown the Greeter Server
        if (endpoint != null) {
            endpoint.stop();
            endpoint = null;
        }
    }

    @Test
    void testCamelGreeter() {
        TestSupport.deleteDirectory("target/greeter/response");
        assertNotNull(camelContext);
        
        ProducerTemplate template = camelContext.createProducerTemplate();
        Object result = template.requestBody("direct:start", REQUEST);
        template.stop();

        assertEquals("Hello Willem", result, "The result is wrong.");
        
        File file = new File("target/greeter/response/response.txt");
        assertTrue(file.exists(), "File " + file + " should be there.");
    }

}
