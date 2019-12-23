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

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelFileGreeterOneWayTest extends CamelSpringTestSupport {
    
    private static final Logger LOG = LoggerFactory.getLogger(CamelGreeterTest.class);
    
    private static Endpoint endpoint;
    private static GreeterImpl greeterImpl;
    private static int port = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("CamelFileGreeterOneWayTest.port", Integer.toString(port));
    }

    @BeforeClass
    public static void startServer() throws Exception {
        // Start the Greeter Server
        greeterImpl = new GreeterImpl();
        String address = "http://localhost:" + port + "/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, greeterImpl);
        LOG.info("The WS endpoint is published! ");
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // Shutdown the Greeter Server
        if (endpoint != null) {
            endpoint.stop();
            endpoint = null;
        }
    }

    @Test
    public void testFileWithOnewayOperation() throws Exception {
        deleteDirectory("target/messages/input/");
        greeterImpl.resetOneWayCounter();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("file://target/messages/input/", "Hello World", Exchange.FILE_NAME, "hello.txt");

        // Sleep a while and wait for the message whole processing
        Thread.sleep(4000);
        template.stop();
        
        // make sure the greeter is called
        assertEquals("The oneway operation of greeter should be called", 1, greeterImpl.getOneWayCounter());

        File file = new File("target/messages/input/hello.txt");
        assertFalse("File " + file + " should be deleted", file.exists());
    }    

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/greeter/CamelFileGreeterOneWayTest.xml");
    }

}
